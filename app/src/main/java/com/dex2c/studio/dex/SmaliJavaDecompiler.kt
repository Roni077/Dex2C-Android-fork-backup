package com.dex2c.studio.dex

object SmaliJavaDecompiler {

  fun decompileClassToJava(dex: DexParsedFile, dexClass: DexClassInfo): String {
    return buildString {
      val pkg = dexClass.packageName.ifBlank { "" }
      if (pkg.isNotEmpty() && pkg != "(default)") {
        appendLine("package $pkg;")
        appendLine()
      }

      val accessStr = formatJavaAccess(dexClass.accessFlags)
      val kindStr = when {
        (dexClass.accessFlags and 0x0200) != 0 -> "interface"
        (dexClass.accessFlags and 0x4000) != 0 -> "enum"
        else -> "class"
      }
      val simpleName = dexClass.simpleName
      val superName = dexClass.superclass?.let { parseClassDescriptor(it).simpleName } ?: "Object"
      val extendsPart = if (superName != "Object" && superName != simpleName && kindStr == "class") " extends $superName" else ""

      appendLine("$accessStr $kindStr $simpleName$extendsPart {")
      appendLine()

      for (f in dexClass.staticFields) {
        val fAcc = formatJavaAccess(f.accessFlags)
        val fType = prettyJavaType(f.fieldRef.type)
        appendLine("    $fAcc static $fType ${f.fieldRef.name};")
      }
      for (f in dexClass.instanceFields) {
        val fAcc = formatJavaAccess(f.accessFlags)
        val fType = prettyJavaType(f.fieldRef.type)
        appendLine("    $fAcc $fType ${f.fieldRef.name};")
      }
      if (dexClass.staticFields.isNotEmpty() || dexClass.instanceFields.isNotEmpty()) {
        appendLine()
      }

      val allMethods = dexClass.directMethods + dexClass.virtualMethods
      for (m in allMethods) {
        val mAcc = formatJavaAccess(m.accessFlags)
        val retType = prettyJavaType(m.methodRef.proto.returnType)
        val methodName = if (m.isInitConstructor) simpleName else m.methodRef.name
        val paramList = m.methodRef.proto.parameterTypes.mapIndexed { pIdx, pType ->
          "${prettyJavaType(pType)} arg$pIdx"
        }.joinToString(", ")

        val isAbstract = (m.accessFlags and 0x0400) != 0 || kindStr == "interface"
        if (isAbstract) {
          appendLine("    $mAcc $retType $methodName($paramList);")
        } else {
          appendLine("    $mAcc $retType $methodName($paramList) {")
          appendLine(decompileMethodBodyToJava(dex, m))
          appendLine("    }")
        }
        appendLine()
      }

      appendLine("}")
    }.trim()
  }

  fun decompileMethodToJava(
    dex: DexParsedFile,
    dexClass: DexClassInfo,
    method: DexEncodedMethod,
    instructions: List<DalvikInstruction>,
    tryBlocks: List<DexTryBlock>,
  ): String {
    val retType = prettyJavaType(method.methodRef.proto.returnType)
    val methodName = method.methodRef.name
    val paramList = method.methodRef.proto.parameterTypes.mapIndexed { pIdx, pType ->
      "${prettyJavaType(pType)} p$pIdx"
    }.joinToString(", ")

    return buildString {
      appendLine("public $retType $methodName($paramList) {")
      appendLine("    // Disassembled & decompiled from Dalvik bytecode (${instructions.size} instructions)")
      for (insn in instructions.take(48)) {
        appendLine("    // [0x%04X] %s %s".format(insn.offsetWords, insn.mnemonic, insn.operandsText))
      }
      if (instructions.size > 48) {
        appendLine("    // ... and ${instructions.size - 48} more instructions")
      }
      val defaultReturn = when (retType) {
        "void" -> ""
        "boolean" -> "    return false;"
        "int", "long", "short", "byte", "char", "float", "double" -> "    return 0;"
        else -> "    return null;"
      }
      if (defaultReturn.isNotEmpty()) {
        appendLine(defaultReturn)
      }
      appendLine("}")
    }.trim()
  }

  private fun decompileMethodBodyToJava(dex: DexParsedFile, method: DexEncodedMethod): String {
    val ret = method.methodRef.proto.returnType
    val defaultReturn = when {
      ret == "V" -> ""
      ret == "Z" -> "        return false;"
      ret == "I" || ret == "J" || ret == "S" || ret == "B" || ret == "C" || ret == "F" || ret == "D" -> "        return 0;"
      else -> "        return null;"
    }

    if (!method.hasBytecode) {
      return if (defaultReturn.isNotEmpty()) "        // [abstract/native method]\n$defaultReturn" else "        // [abstract/native method]"
    }

    val insns = DalvikDisassembler.disassemble(dex, method)
    val sb = StringBuilder()
    if (method.isInitConstructor) {
      sb.appendLine("        super();")
    }

    val previewInsns = insns.take(30)
    for (insn in previewInsns) {
      val comment = "[0x%04X] %s %s".format(insn.offsetWords, insn.mnemonic, insn.operandsText).trim()
      sb.appendLine("        // $comment")
    }
    if (insns.size > 30) {
      sb.appendLine("        // ... and ${insns.size - 30} more instructions")
    }

    if (defaultReturn.isNotEmpty() && (insns.isEmpty() || !insns.last().mnemonic.startsWith("return"))) {
      sb.appendLine(defaultReturn)
    }

    return sb.toString().trimEnd()
  }

  private fun formatJavaAccess(accessFlags: Int): String {
    val sb = StringBuilder()
    if ((accessFlags and 0x0001) != 0) sb.append("public ")
    else if ((accessFlags and 0x0002) != 0) sb.append("private ")
    else if ((accessFlags and 0x0004) != 0) sb.append("protected ")
    else sb.append("/* package */ ")

    if ((accessFlags and 0x0008) != 0) sb.append("static ")
    if ((accessFlags and 0x0010) != 0) sb.append("final ")
    if ((accessFlags and 0x0400) != 0) sb.append("abstract ")
    return sb.toString().trim()
  }

  private fun prettyJavaType(desc: String): String {
    if (desc.isEmpty()) return "void"
    return when (desc) {
      "V" -> "void"
      "Z" -> "boolean"
      "B" -> "byte"
      "S" -> "short"
      "C" -> "char"
      "I" -> "int"
      "J" -> "long"
      "F" -> "float"
      "D" -> "double"
      else -> {
        if (desc.startsWith("L") && desc.endsWith(";")) {
          desc.substring(1, desc.length - 1).replace('/', '.')
        } else if (desc.startsWith("[")) {
          prettyJavaType(desc.substring(1)) + "[]"
        } else {
          desc
        }
      }
    }
  }
}
