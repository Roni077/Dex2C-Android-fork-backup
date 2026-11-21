//! Dalvik bytecode instructions, opcode definitions, operands, and payload structures.

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum OpcodeGroup {
    Move,
    Return,
    Const,
    Monitor,
    CheckCast,
    InstanceOf,
    ArrayLength,
    NewInstance,
    NewArray,
    FilledNewArray,
    FillArrayData,
    Throw,
    Goto,
    PackedSwitch,
    SparseSwitch,
    Cmp,
    IfTest,
    IfTestZ,
    ArrayGet,
    ArrayPut,
    InstanceFieldGet,
    InstanceFieldPut,
    StaticFieldGet,
    StaticFieldPut,
    InvokeVirtual,
    InvokeSuper,
    InvokeDirect,
    InvokeStatic,
    InvokeInterface,
    InvokeVirtualRange,
    InvokeSuperRange,
    InvokeDirectRange,
    InvokeStaticRange,
    InvokeInterfaceRange,
    Unop,
    Binop,
    Binop2Addr,
    BinopLit16,
    BinopLit8,
    Nop,
    Unknown,
}

#[derive(Debug, Clone, PartialEq)]
pub enum DalvikPayload {
    PackedSwitch {
        first_key: i32,
        targets: Vec<i32>,
    },
    SparseSwitch {
        keys: Vec<i32>,
        targets: Vec<i32>,
    },
    FillArrayData {
        element_width: u16,
        data: Vec<u8>,
    },
}

#[derive(Debug, Clone, PartialEq)]
pub struct DalvikInstruction {
    pub offset: u32,
    pub opcode: u16,
    pub mnemonic: &'static str,
    pub group: OpcodeGroup,
    pub length_units: u16, // in 16-bit code units
    pub registers: Vec<u16>,
    pub literal: Option<i64>,
    pub target_offset: Option<i32>, // branch/jump offset in code units
    pub index: Option<u32>,         // string, type, field, or method index
    pub payload: Option<DalvikPayload>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct CatchHandler {
    pub type_desc: String,
    pub handler_pc: u32,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct TryCatchBlock {
    pub start_pc: u32,
    pub end_pc: u32,
    pub handlers: Vec<CatchHandler>,
    pub catch_all_pc: Option<u32>,
}
