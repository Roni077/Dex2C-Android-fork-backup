//! Background task manager, atomic cancellation tokens, and progress reporting.

use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::Arc;

pub type ProgressCallback = Box<dyn Fn(&str, f32, &str) + Send + Sync + 'static>;

#[derive(Clone)]
pub struct CancellationToken {
    cancelled: Arc<AtomicBool>,
}

impl CancellationToken {
    pub fn new() -> Self {
        Self {
            cancelled: Arc::new(AtomicBool::new(false)),
        }
    }

    pub fn cancel(&self) {
        self.cancelled.store(true, Ordering::SeqCst);
    }

    pub fn is_cancelled(&self) -> bool {
        self.cancelled.load(Ordering::SeqCst)
    }

    pub fn check(&self) -> Result<(), crate::error::Dex2cError> {
        if self.is_cancelled() {
            Err(crate::error::Dex2cError::Cancelled)
        } else {
            Ok(())
        }
    }
}

impl Default for CancellationToken {
    fn default() -> Self {
        Self::new()
    }
}

static NEXT_TASK_ID: AtomicU64 = AtomicU64::new(1);

pub struct TaskHandle {
    pub id: u64,
    pub token: CancellationToken,
}

impl TaskHandle {
    pub fn new() -> Self {
        let id = NEXT_TASK_ID.fetch_add(1, Ordering::SeqCst);
        Self {
            id,
            token: CancellationToken::new(),
        }
    }
}

impl Default for TaskHandle {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_cancellation_token() {
        let token = CancellationToken::new();
        assert!(!token.is_cancelled());
        assert!(token.check().is_ok());

        token.cancel();
        assert!(token.is_cancelled());
        assert!(token.check().is_err());
    }
}
