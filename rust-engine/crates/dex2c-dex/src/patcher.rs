//! In-place DEX bytecode method patcher and checksum re-signer.
//! Replaces transpiled method bytecode with native stubs (ACC_NATIVE = 0x0100, code_off = 0)
//! and recomputes the Adler32 checksum and SHA-1 signature directly in-memory.

use crate::parser::read_uleb128;
use dex2c_core::{Dex2cError, Result};
use std::collections::HashSet;

pub const ACC_NATIVE: u32 = 0x0100;

pub fn patch_dex_methods_native(dex_bytes: &mut [u8], native_method_indices: &HashSet<u32>) -> Result<usize> {
    if dex_bytes.len() < 0x70 {
        return Err(Dex2cError::DexFormat("DEX too small to patch".into()));
    }

    let class_defs_size = u32::from_le_bytes([
        dex_bytes[0x60], dex_bytes[0x61], dex_bytes[0x62], dex_bytes[0x63],
    ]) as usize;

    let class_defs_off = u32::from_le_bytes([
        dex_bytes[0x64], dex_bytes[0x65], dex_bytes[0x66], dex_bytes[0x67],
    ]) as usize;

    let mut patched_count = 0usize;

    for c in 0..class_defs_size {
        let base = class_defs_off + c * 32;
        if base + 32 > dex_bytes.len() {
            break;
        }

        let class_data_off = u32::from_le_bytes([
            dex_bytes[base + 24], dex_bytes[base + 25], dex_bytes[base + 26], dex_bytes[base + 27],
        ]) as usize;

        if class_data_off == 0 || class_data_off >= dex_bytes.len() {
            continue;
        }

        // Class data item layout:
        // uleb128 static_fields_size
        // uleb128 instance_fields_size
        // uleb128 direct_methods_size
        // uleb128 virtual_methods_size
        let mut cursor = class_data_off;
        let (static_fields, c1) = match read_uleb128(dex_bytes, cursor) { Some(p) => p, None => continue };
        let (instance_fields, c2) = match read_uleb128(dex_bytes, c1) { Some(p) => p, None => continue };
        let (direct_methods, c3) = match read_uleb128(dex_bytes, c2) { Some(p) => p, None => continue };
        let (virtual_methods, c4) = match read_uleb128(dex_bytes, c3) { Some(p) => p, None => continue };
        cursor = c4;

        // Skip fields
        for _ in 0..static_fields {
            let (_, p1) = match read_uleb128(dex_bytes, cursor) { Some(p) => p, None => break };
            let (_, p2) = match read_uleb128(dex_bytes, p1) { Some(p) => p, None => break };
            cursor = p2;
        }
        for _ in 0..instance_fields {
            let (_, p1) = match read_uleb128(dex_bytes, cursor) { Some(p) => p, None => break };
            let (_, p2) = match read_uleb128(dex_bytes, p1) { Some(p) => p, None => break };
            cursor = p2;
        }

        // Patch direct methods
        let mut last_idx = 0u32;
        for _ in 0..direct_methods {
            let (idx_diff, p1) = match read_uleb128(dex_bytes, cursor) { Some(p) => p, None => break };
            let (flags, p2) = match read_uleb128(dex_bytes, p1) { Some(p) => p, None => break };
            let (code_off, p3) = match read_uleb128(dex_bytes, p2) { Some(p) => p, None => break };
            let entry_end = p3;
            last_idx += idx_diff;

            if native_method_indices.contains(&last_idx) && code_off != 0 {
                // Method to patch: set ACC_NATIVE and code_off = 0
                let new_flags = flags | ACC_NATIVE;
                let mut re_encoded = Vec::with_capacity(8);
                encode_uleb128(&mut re_encoded, idx_diff);
                encode_uleb128(&mut re_encoded, new_flags);
                encode_uleb128(&mut re_encoded, 0); // code_off = 0

                // If sizes match, write directly in-place
                let original_len = entry_end - cursor;
                if re_encoded.len() <= original_len {
                    dex_bytes[cursor..cursor + re_encoded.len()].copy_from_slice(&re_encoded);
                    // Pad remaining bytes if any with NOPs or keep cursor consistent
                }
                patched_count += 1;
            }
            cursor = entry_end;
        }

        // Patch virtual methods
        last_idx = 0;
        for _ in 0..virtual_methods {
            let (idx_diff, p1) = match read_uleb128(dex_bytes, cursor) { Some(p) => p, None => break };
            let (flags, p2) = match read_uleb128(dex_bytes, p1) { Some(p) => p, None => break };
            let (code_off, p3) = match read_uleb128(dex_bytes, p2) { Some(p) => p, None => break };
            let entry_end = p3;
            last_idx += idx_diff;

            if native_method_indices.contains(&last_idx) && code_off != 0 {
                let new_flags = flags | ACC_NATIVE;
                let mut re_encoded = Vec::with_capacity(8);
                encode_uleb128(&mut re_encoded, idx_diff);
                encode_uleb128(&mut re_encoded, new_flags);
                encode_uleb128(&mut re_encoded, 0);

                let original_len = entry_end - cursor;
                if re_encoded.len() <= original_len {
                    dex_bytes[cursor..cursor + re_encoded.len()].copy_from_slice(&re_encoded);
                }
                patched_count += 1;
            }
            cursor = entry_end;
        }
    }

    // Recompute signatures
    resign_dex_header(dex_bytes)?;

    Ok(patched_count)
}

pub fn resign_dex_header(dex_bytes: &mut [u8]) -> Result<()> {
    if dex_bytes.len() < 0x70 {
        return Err(Dex2cError::DexFormat("DEX too small to sign".into()));
    }

    // 1. SHA-1 signature covers bytes from 0x20 to EOF
    let sha1_digest = compute_sha1(&dex_bytes[0x20..]);
    dex_bytes[0x0C..0x20].copy_from_slice(&sha1_digest);

    // 2. Adler32 checksum covers bytes from 0x0C to EOF
    let checksum = compute_adler32(&dex_bytes[0x0C..]);
    dex_bytes[0x08..0x0C].copy_from_slice(&checksum.to_le_bytes());

    Ok(())
}

fn compute_adler32(data: &[u8]) -> u32 {
    let mut a: u32 = 1;
    let mut b: u32 = 0;
    for &byte in data {
        a = (a + byte as u32) % 65521;
        b = (b + a) % 65521;
    }
    (b << 16) | a
}

fn compute_sha1(data: &[u8]) -> [u8; 20] {
    let mut h0: u32 = 0x67452301;
    let mut h1: u32 = 0xEFCDAB89;
    let mut h2: u32 = 0x98BADCFE;
    let mut h3: u32 = 0x10325476;
    let mut h4: u32 = 0xC3D2E1F0;

    let ml = (data.len() as u64) * 8;
    let mut msg = data.to_vec();
    msg.push(0x80);
    while (msg.len() % 64) != 56 {
        msg.push(0x00);
    }
    msg.extend_from_slice(&ml.to_be_bytes());

    for chunk in msg.chunks_exact(64) {
        let mut w = [0u32; 80];
        for i in 0..16 {
            w[i] = u32::from_be_bytes([chunk[i * 4], chunk[i * 4 + 1], chunk[i * 4 + 2], chunk[i * 4 + 3]]);
        }
        for i in 16..80 {
            w[i] = (w[i - 3] ^ w[i - 8] ^ w[i - 14] ^ w[i - 16]).rotate_left(1);
        }

        let mut a = h0;
        let mut b = h1;
        let mut c = h2;
        let mut d = h3;
        let mut e = h4;

        for i in 0..80 {
            let (f, k) = match i {
                0..=19 => ((b & c) | ((!b) & d), 0x5A827999),
                20..=39 => (b ^ c ^ d, 0x6ED9EBA1),
                40..=59 => ((b & c) | (b & d) | (c & d), 0x8F1BBCDC),
                _ => (b ^ c ^ d, 0xCA62C1D6),
            };
            let temp = a.rotate_left(5).wrapping_add(f).wrapping_add(e).wrapping_add(k).wrapping_add(w[i]);
            e = d;
            d = c;
            c = b.rotate_left(30);
            b = a;
            a = temp;
        }

        h0 = h0.wrapping_add(a);
        h1 = h1.wrapping_add(b);
        h2 = h2.wrapping_add(c);
        h3 = h3.wrapping_add(d);
        h4 = h4.wrapping_add(e);
    }

    let mut out = [0u8; 20];
    out[0..4].copy_from_slice(&h0.to_be_bytes());
    out[4..8].copy_from_slice(&h1.to_be_bytes());
    out[8..12].copy_from_slice(&h2.to_be_bytes());
    out[12..16].copy_from_slice(&h3.to_be_bytes());
    out[16..20].copy_from_slice(&h4.to_be_bytes());
    out
}

fn encode_uleb128(out: &mut Vec<u8>, mut val: u32) {
    loop {
        let mut byte = (val & 0x7F) as u8;
        val >>= 7;
        if val != 0 {
            byte |= 0x80;
            out.push(byte);
        } else {
            out.push(byte);
            break;
        }
    }
}
