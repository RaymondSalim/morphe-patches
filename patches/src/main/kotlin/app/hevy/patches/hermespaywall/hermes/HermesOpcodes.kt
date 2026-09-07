package app.hevy.patches.hermespaywall.hermes

/**
 * Hermes bytecode version 96 opcode table.
 *
 * [TABLE] is indexed by opcode value. [Opcode.size] is the fixed instruction
 * size in bytes (1 for the opcode byte itself + operand bytes). String-id
 * operands are listed with their absolute offset within the instruction and
 * their integer width.
 */
internal class Opcode internal constructor(
    val size: Int,
    val stringOperands: List<StringOperand>,
)

internal class StringOperand internal constructor(
    val offset: Int,
    val width: Int,
)

internal object HermesOpcodes {
    const val GET_ENVIRONMENT = 0x29
    const val PUT_NEW_OWN_BY_ID = 0x40
    const val RET = 0x5C
    const val LOAD_CONST_TRUE = 0x78
    const val LOAD_CONST_FALSE = 0x79
    const val LOAD_THIS_NS = 0x7C
    const val JMP_TRUE = 0x90

    val TABLE: Array<Opcode> = arrayOf(
        Opcode(1, emptyList()),   // 0x00 Unreachable
        Opcode(10, emptyList()),  // 0x01 NewObjectWithBuffer
        Opcode(14, emptyList()),  // 0x02 NewObjectWithBufferLong
        Opcode(2, emptyList()),   // 0x03 NewObject
        Opcode(3, emptyList()),   // 0x04 NewObjectWithParent
        Opcode(8, emptyList()),   // 0x05 NewArrayWithBuffer
        Opcode(10, emptyList()),  // 0x06 NewArrayWithBufferLong
        Opcode(4, emptyList()),   // 0x07 NewArray
        Opcode(3, emptyList()),   // 0x08 Mov
        Opcode(9, emptyList()),   // 0x09 MovLong
        Opcode(3, emptyList()),   // 0x0A Negate
        Opcode(3, emptyList()),   // 0x0B Not
        Opcode(3, emptyList()),   // 0x0C BitNot
        Opcode(3, emptyList()),   // 0x0D TypeOf
        Opcode(4, emptyList()),   // 0x0E Eq
        Opcode(4, emptyList()),   // 0x0F StrictEq
        Opcode(4, emptyList()),   // 0x10 Neq
        Opcode(4, emptyList()),   // 0x11 StrictNeq
        Opcode(4, emptyList()),   // 0x12 Less
        Opcode(4, emptyList()),   // 0x13 LessEq
        Opcode(4, emptyList()),   // 0x14 Greater
        Opcode(4, emptyList()),   // 0x15 GreaterEq
        Opcode(4, emptyList()),   // 0x16 Add
        Opcode(4, emptyList()),   // 0x17 AddN
        Opcode(4, emptyList()),   // 0x18 Mul
        Opcode(4, emptyList()),   // 0x19 MulN
        Opcode(4, emptyList()),   // 0x1A Div
        Opcode(4, emptyList()),   // 0x1B DivN
        Opcode(4, emptyList()),   // 0x1C Mod
        Opcode(4, emptyList()),   // 0x1D Sub
        Opcode(4, emptyList()),   // 0x1E SubN
        Opcode(4, emptyList()),   // 0x1F LShift
        Opcode(4, emptyList()),   // 0x20 RShift
        Opcode(4, emptyList()),   // 0x21 URshift
        Opcode(4, emptyList()),   // 0x22 BitAnd
        Opcode(4, emptyList()),   // 0x23 BitXor
        Opcode(4, emptyList()),   // 0x24 BitOr
        Opcode(3, emptyList()),   // 0x25 Inc
        Opcode(3, emptyList()),   // 0x26 Dec
        Opcode(4, emptyList()),   // 0x27 InstanceOf
        Opcode(4, emptyList()),   // 0x28 IsIn
        Opcode(3, emptyList()),   // 0x29 GetEnvironment
        Opcode(4, emptyList()),   // 0x2A StoreToEnvironment
        Opcode(5, emptyList()),   // 0x2B StoreToEnvironmentL
        Opcode(4, emptyList()),   // 0x2C StoreNPToEnvironment
        Opcode(5, emptyList()),   // 0x2D StoreNPToEnvironmentL
        Opcode(4, emptyList()),   // 0x2E LoadFromEnvironment
        Opcode(5, emptyList()),   // 0x2F LoadFromEnvironmentL
        Opcode(2, emptyList()),   // 0x30 GetGlobalObject
        Opcode(2, emptyList()),   // 0x31 GetNewTarget
        Opcode(2, emptyList()),   // 0x32 CreateEnvironment
        Opcode(7, emptyList()),   // 0x33 CreateInnerEnvironment
        Opcode(5, listOf(StringOperand(1, 4))),  // 0x34 DeclareGlobalVar
        Opcode(5, listOf(StringOperand(1, 4))),  // 0x35 ThrowIfHasRestrictedGlobalProperty
        Opcode(5, listOf(StringOperand(4, 1))),  // 0x36 GetByIdShort
        Opcode(6, listOf(StringOperand(4, 2))),  // 0x37 GetById
        Opcode(8, listOf(StringOperand(4, 4))),  // 0x38 GetByIdLong
        Opcode(6, listOf(StringOperand(4, 2))),  // 0x39 TryGetById
        Opcode(8, listOf(StringOperand(4, 4))),  // 0x3A TryGetByIdLong
        Opcode(6, listOf(StringOperand(4, 2))),  // 0x3B PutById
        Opcode(8, listOf(StringOperand(4, 4))),  // 0x3C PutByIdLong
        Opcode(6, listOf(StringOperand(4, 2))),  // 0x3D TryPutById
        Opcode(8, listOf(StringOperand(4, 4))),  // 0x3E TryPutByIdLong
        Opcode(4, listOf(StringOperand(3, 1))),  // 0x3F PutNewOwnByIdShort
        Opcode(5, listOf(StringOperand(3, 2))),  // 0x40 PutNewOwnById
        Opcode(7, listOf(StringOperand(3, 4))),  // 0x41 PutNewOwnByIdLong
        Opcode(5, listOf(StringOperand(3, 2))),  // 0x42 PutNewOwnNEById
        Opcode(7, listOf(StringOperand(3, 4))),  // 0x43 PutNewOwnNEByIdLong
        Opcode(4, emptyList()),   // 0x44 PutOwnByIndex
        Opcode(7, emptyList()),   // 0x45 PutOwnByIndexL
        Opcode(5, emptyList()),   // 0x46 PutOwnByVal
        Opcode(5, listOf(StringOperand(3, 2))),  // 0x47 DelById
        Opcode(7, listOf(StringOperand(3, 4))),  // 0x48 DelByIdLong
        Opcode(4, emptyList()),   // 0x49 GetByVal
        Opcode(4, emptyList()),   // 0x4A PutByVal
        Opcode(4, emptyList()),   // 0x4B DelByVal
        Opcode(6, emptyList()),   // 0x4C PutOwnGetterSetterByVal
        Opcode(5, emptyList()),   // 0x4D GetPNameList
        Opcode(6, emptyList()),   // 0x4E GetNextPName
        Opcode(4, emptyList()),   // 0x4F Call
        Opcode(4, emptyList()),   // 0x50 Construct
        Opcode(4, emptyList()),   // 0x51 Call1
        Opcode(5, listOf(StringOperand(3, 2))),  // 0x52 CallDirect
        Opcode(5, emptyList()),   // 0x53 Call2
        Opcode(6, emptyList()),   // 0x54 Call3
        Opcode(7, emptyList()),   // 0x55 Call4
        Opcode(7, emptyList()),   // 0x56 CallLong
        Opcode(7, emptyList()),   // 0x57 ConstructLong
        Opcode(7, emptyList()),   // 0x58 CallDirectLongIndex
        Opcode(4, emptyList()),   // 0x59 CallBuiltin
        Opcode(7, emptyList()),   // 0x5A CallBuiltinLong
        Opcode(3, emptyList()),   // 0x5B GetBuiltinClosure
        Opcode(2, emptyList()),   // 0x5C Ret
        Opcode(2, emptyList()),   // 0x5D Catch
        Opcode(4, emptyList()),   // 0x5E DirectEval
        Opcode(2, emptyList()),   // 0x5F Throw
        Opcode(3, emptyList()),   // 0x60 ThrowIfEmpty
        Opcode(1, emptyList()),   // 0x61 Debugger
        Opcode(1, emptyList()),   // 0x62 AsyncBreakCheck
        Opcode(3, emptyList()),   // 0x63 ProfilePoint
        Opcode(5, listOf(StringOperand(3, 2))),  // 0x64 CreateClosure
        Opcode(7, listOf(StringOperand(3, 4))),  // 0x65 CreateClosureLongIndex
        Opcode(5, listOf(StringOperand(3, 2))),  // 0x66 CreateGeneratorClosure
        Opcode(7, listOf(StringOperand(3, 4))),  // 0x67 CreateGeneratorClosureLongIndex
        Opcode(5, listOf(StringOperand(3, 2))),  // 0x68 CreateAsyncClosure
        Opcode(7, listOf(StringOperand(3, 4))),  // 0x69 CreateAsyncClosureLongIndex
        Opcode(4, emptyList()),   // 0x6A CreateThis
        Opcode(4, emptyList()),   // 0x6B SelectObject
        Opcode(3, emptyList()),   // 0x6C LoadParam
        Opcode(6, emptyList()),   // 0x6D LoadParamLong
        Opcode(3, emptyList()),   // 0x6E LoadConstUInt8
        Opcode(6, emptyList()),   // 0x6F LoadConstInt
        Opcode(10, emptyList()),  // 0x70 LoadConstDouble
        Opcode(4, listOf(StringOperand(2, 2))),  // 0x71 LoadConstBigInt
        Opcode(6, listOf(StringOperand(2, 4))),  // 0x72 LoadConstBigIntLongIndex
        Opcode(4, listOf(StringOperand(2, 2))),  // 0x73 LoadConstString
        Opcode(6, listOf(StringOperand(2, 4))),  // 0x74 LoadConstStringLongIndex
        Opcode(2, emptyList()),   // 0x75 LoadConstEmpty
        Opcode(2, emptyList()),   // 0x76 LoadConstUndefined
        Opcode(2, emptyList()),   // 0x77 LoadConstNull
        Opcode(2, emptyList()),   // 0x78 LoadConstTrue
        Opcode(2, emptyList()),   // 0x79 LoadConstFalse
        Opcode(2, emptyList()),   // 0x7A LoadConstZero
        Opcode(3, emptyList()),   // 0x7B CoerceThisNS
        Opcode(2, emptyList()),   // 0x7C LoadThisNS
        Opcode(3, emptyList()),   // 0x7D ToNumber
        Opcode(3, emptyList()),   // 0x7E ToNumeric
        Opcode(3, emptyList()),   // 0x7F ToInt32
        Opcode(3, emptyList()),   // 0x80 AddEmptyString
        Opcode(4, emptyList()),   // 0x81 GetArgumentsPropByVal
        Opcode(3, emptyList()),   // 0x82 GetArgumentsLength
        Opcode(2, emptyList()),   // 0x83 ReifyArguments
        Opcode(14, listOf(StringOperand(2, 4), StringOperand(6, 4))),  // 0x84 CreateRegExp
        Opcode(18, emptyList()),  // 0x85 SwitchImm
        Opcode(1, emptyList()),   // 0x86 StartGenerator
        Opcode(3, emptyList()),   // 0x87 ResumeGenerator
        Opcode(1, emptyList()),   // 0x88 CompleteGenerator
        Opcode(5, listOf(StringOperand(3, 2))),  // 0x89 CreateGenerator
        Opcode(7, listOf(StringOperand(3, 4))),  // 0x8A CreateGeneratorLongIndex
        Opcode(3, emptyList()),   // 0x8B IteratorBegin
        Opcode(4, emptyList()),   // 0x8C IteratorNext
        Opcode(3, emptyList()),   // 0x8D IteratorClose
        Opcode(2, emptyList()),   // 0x8E Jmp
        Opcode(5, emptyList()),   // 0x8F JmpLong
        Opcode(3, emptyList()),   // 0x90 JmpTrue
        Opcode(6, emptyList()),   // 0x91 JmpTrueLong
        Opcode(3, emptyList()),   // 0x92 JmpFalse
        Opcode(6, emptyList()),   // 0x93 JmpFalseLong
        Opcode(3, emptyList()),   // 0x94 JmpUndefined
        Opcode(6, emptyList()),   // 0x95 JmpUndefinedLong
        Opcode(2, emptyList()),   // 0x96 SaveGenerator
        Opcode(5, emptyList()),   // 0x97 SaveGeneratorLong
        Opcode(4, emptyList()),   // 0x98 JLess
        Opcode(7, emptyList()),   // 0x99 JLessLong
        Opcode(4, emptyList()),   // 0x9A JNotLess
        Opcode(7, emptyList()),   // 0x9B JNotLessLong
        Opcode(4, emptyList()),   // 0x9C JLessN
        Opcode(7, emptyList()),   // 0x9D JLessNLong
        Opcode(4, emptyList()),   // 0x9E JNotLessN
        Opcode(7, emptyList()),   // 0x9F JNotLessNLong
        Opcode(4, emptyList()),   // 0xA0 JLessEqual
        Opcode(7, emptyList()),   // 0xA1 JLessEqualLong
        Opcode(4, emptyList()),   // 0xA2 JNotLessEqual
        Opcode(7, emptyList()),   // 0xA3 JNotLessEqualLong
        Opcode(4, emptyList()),   // 0xA4 JLessEqualN
        Opcode(7, emptyList()),   // 0xA5 JLessEqualNLong
        Opcode(4, emptyList()),   // 0xA6 JNotLessEqualN
        Opcode(7, emptyList()),   // 0xA7 JNotLessEqualNLong
        Opcode(4, emptyList()),   // 0xA8 JGreater
        Opcode(7, emptyList()),   // 0xA9 JGreaterLong
        Opcode(4, emptyList()),   // 0xAA JNotGreater
        Opcode(7, emptyList()),   // 0xAB JNotGreaterLong
        Opcode(4, emptyList()),   // 0xAC JGreaterN
        Opcode(7, emptyList()),   // 0xAD JGreaterNLong
        Opcode(4, emptyList()),   // 0xAE JNotGreaterN
        Opcode(7, emptyList()),   // 0xAF JNotGreaterNLong
        Opcode(4, emptyList()),   // 0xB0 JGreaterEqual
        Opcode(7, emptyList()),   // 0xB1 JGreaterEqualLong
        Opcode(4, emptyList()),   // 0xB2 JNotGreaterEqual
        Opcode(7, emptyList()),   // 0xB3 JNotGreaterEqualLong
        Opcode(4, emptyList()),   // 0xB4 JGreaterEqualN
        Opcode(7, emptyList()),   // 0xB5 JGreaterEqualNLong
        Opcode(4, emptyList()),   // 0xB6 JNotGreaterEqualN
        Opcode(7, emptyList()),   // 0xB7 JNotGreaterEqualNLong
        Opcode(4, emptyList()),   // 0xB8 JEqual
        Opcode(7, emptyList()),   // 0xB9 JEqualLong
        Opcode(4, emptyList()),   // 0xBA JNotEqual
        Opcode(7, emptyList()),   // 0xBB JNotEqualLong
        Opcode(4, emptyList()),   // 0xBC JStrictEqual
        Opcode(7, emptyList()),   // 0xBD JStrictEqualLong
        Opcode(4, emptyList()),   // 0xBE JStrictNotEqual
        Opcode(7, emptyList()),   // 0xBF JStrictNotEqualLong
        Opcode(4, emptyList()),   // 0xC0 Add32
        Opcode(4, emptyList()),   // 0xC1 Sub32
        Opcode(4, emptyList()),   // 0xC2 Mul32
        Opcode(4, emptyList()),   // 0xC3 Divi32
        Opcode(4, emptyList()),   // 0xC4 Divu32
        Opcode(4, emptyList()),   // 0xC5 Loadi8
        Opcode(4, emptyList()),   // 0xC6 Loadu8
        Opcode(4, emptyList()),   // 0xC7 Loadi16
        Opcode(4, emptyList()),   // 0xC8 Loadu16
        Opcode(4, emptyList()),   // 0xC9 Loadi32
        Opcode(4, emptyList()),   // 0xCA Loadu32
        Opcode(4, emptyList()),   // 0xCB Store8
        Opcode(4, emptyList()),   // 0xCC Store16
        Opcode(4, emptyList()),   // 0xCD Store32
    )
}
