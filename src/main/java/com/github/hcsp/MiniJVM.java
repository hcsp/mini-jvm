package com.github.hcsp;

import com.github.zxh.classpy.classfile.ClassFile;
import com.github.zxh.classpy.classfile.ClassFileParser;
import com.github.zxh.classpy.classfile.MethodInfo;
import com.github.zxh.classpy.classfile.bytecode.Bipush;
import com.github.zxh.classpy.classfile.bytecode.Instruction;
import com.github.zxh.classpy.classfile.bytecode.InstructionCp2;
import com.github.zxh.classpy.classfile.bytecode.Sipush;
import com.github.zxh.classpy.classfile.constant.ConstantClassInfo;
import com.github.zxh.classpy.classfile.constant.ConstantFieldrefInfo;
import com.github.zxh.classpy.classfile.constant.ConstantMethodrefInfo;
import com.github.zxh.classpy.classfile.constant.ConstantNameAndTypeInfo;
import com.github.zxh.classpy.classfile.constant.ConstantPool;
import com.sun.org.apache.bcel.internal.generic.IFNE;
import com.sun.xml.internal.bind.v2.runtime.output.Pcdata;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;
import java.util.Stack;
import java.util.stream.Stream;

/**
 * 这是一个用来学习的JVM
 */
public class MiniJVM {
    private String mainClass;
    private String[] classPathEntries;

    public static void main(String[] args) {
        new MiniJVM("target/classes", "com.github.hcsp.RecursiveClass").start();
    }

    /**
     * 创建一个迷你JVM，使用指定的classpath和main class
     *
     * @param classPath 启动时的classpath，使用{@link java.io.File#pathSeparator}的分隔符，我们支持文件夹
     */
    public MiniJVM(String classPath, String mainClass) {
        this.mainClass = mainClass;
        this.classPathEntries = classPath.split(File.pathSeparator);
    }

    /**
     * 启动并运行该虚拟机
     */
    public void start() {
        ClassFile mainClassFile = loadClassFromClassPath(mainClass);

        MethodInfo methodInfo = mainClassFile.getMethod("main").get(0);

        Stack<StackFrame> methodStack = new Stack<>();

        Object[] localVariablesForMainStackFrame = new Object[methodInfo.getMaxStack()];
        localVariablesForMainStackFrame[0] = null;

        methodStack.push(new StackFrame(localVariablesForMainStackFrame, methodInfo, mainClassFile));

        PCRegister pcRegister = new PCRegister(methodStack);

        while (true) {
            Instruction instruction = pcRegister.getNextInstruction();
            if (instruction == null) {
                break;
            }
            switch (instruction.getOpcode()) {
                case getstatic: {
                    int fieldIndex = InstructionCp2.class.cast(instruction).getTargetFieldIndex();
                    ConstantPool constantPool = pcRegister.getTopFrameClassConstantPool();
                    ConstantFieldrefInfo fieldrefInfo = constantPool.getFieldrefInfo(fieldIndex);
                    ConstantClassInfo classInfo = fieldrefInfo.getClassInfo(constantPool);
                    ConstantNameAndTypeInfo nameAndTypeInfo = fieldrefInfo.getFieldNameAndTypeInfo(constantPool);

                    String className = constantPool.getUtf8String(classInfo.getNameIndex());
                    String fieldName = nameAndTypeInfo.getName(constantPool);

                    if ("java/lang/System".equals(className) && "out".equals(fieldName)) {
                        Object field = System.out;
                        pcRegister.getTopFrame().pushObjectToOperandStack(field);
                    } else {
                        throw new IllegalStateException("Not implemented yet!");
                    }
                }
                break;
                case invokestatic: {
                    String className = getClassNameFromInvokeInstruction(instruction, pcRegister.getTopFrameClassConstantPool());
                    String methodName = getMethodNameFromInvokeInstruction(instruction, pcRegister.getTopFrameClassConstantPool());
                    ClassFile classFile = loadClassFromClassPath(className);
                    MethodInfo targetMethodInfo = classFile.getMethod(methodName).get(0);

                    Object[] localVariables = new Object[targetMethodInfo.getMaxLocals()];

                    // TODO 应该分析方法的参数，从操作数栈上弹出对应数量的参数放在新栈帧的局部变量表中
                    String methodDesc = targetMethodInfo.getParts().get(2).getDesc();
                    if (methodDesc.contains("(I)")) {
                        localVariables[0] = pcRegister.getTopFrame().operandStack.pop();
                    }
                    StackFrame newFrame = new StackFrame(localVariables, targetMethodInfo, classFile);
                    methodStack.push(newFrame);
                }
                break;
                case bipush: {
                    Bipush bipush = (Bipush) instruction;
                    pcRegister.getTopFrame().pushObjectToOperandStack(bipush.getOperand());
                }
                break;
                case ireturn: {
                    Object returnValue = pcRegister.getTopFrame().popFromOperandStack();
                    pcRegister.popFrameFromMethodStack();
                    pcRegister.getTopFrame().pushObjectToOperandStack(returnValue);
                }
                break;
                case invokevirtual: {
                    String className = getClassNameFromInvokeInstruction(instruction, pcRegister.getTopFrameClassConstantPool());
                    String methodName = getMethodNameFromInvokeInstruction(instruction, pcRegister.getTopFrameClassConstantPool());
                    if ("java/io/PrintStream".equals(className) && "println".equals(methodName)) {
                        Object param = pcRegister.getTopFrame().popFromOperandStack();
                        Object thisObject = pcRegister.getTopFrame().popFromOperandStack();
                        System.out.println(param);
                    } else {
                        throw new IllegalStateException("Not implemented yet!");
                    }
                }
                break;
                case _return:
                    pcRegister.popFrameFromMethodStack();
                    break;
                case iload_0:
                    Object firstVar = pcRegister.getTopFrame().localVariables[0];
                    pcRegister.getTopFrame().pushObjectToOperandStack(firstVar);
                    break;
                case ifne:
                    int topNum = (int) pcRegister.getTopFrame().operandStack.peek();
                    if (topNum != 0) {
                        pcRegister.getTopFrame().setCurrentInstructionIndexByIfne(instruction);
                    }
                    break;
                case sipush:
                    String siPushDesc = instruction.getDesc();
                    pcRegister.getTopFrame().operandStack.push(siPushDesc.split(" ")[1]);
                    break;
                case iconst_5:
                case iconst_1:
                case iconst_2:
                    setConstVal(pcRegister, instruction);
                    break;
                case isub:
                    calculation2NumAndSetOperandStack(pcRegister, "-");
                    break;
                case irem:
                    calculation2NumAndSetOperandStack(pcRegister, "%");
                    break;
                case imul:
                    calculation2NumAndSetOperandStack(pcRegister, "*");
                    break;
                default:
                    throw new IllegalStateException("Opcode " + instruction + " not implemented yet!");
            }
        }
    }

    private void calculation2NumAndSetOperandStack(PCRegister pcRegister, String operator) {
        int topNum = (int) pcRegister.getTopFrame().operandStack.pop();
        int footNum = (int) pcRegister.getTopFrame().operandStack.pop();
        int result;
        switch (operator) {
            case "+":
                result = topNum + footNum;
                break;
            case "-":
                result = footNum - topNum;
                break;
            case "*":
                result = footNum * topNum;
                break;
            case "/":
                result = footNum / topNum;
                break;
            case "%":
                result = footNum % topNum;
                break;
            default:
                throw new IllegalArgumentException("操作符非法：" + operator);
        }
        pcRegister.getTopFrame().operandStack.push(result);
    }

    private void setConstVal(PCRegister pcRegister, Instruction instruction) {
        pcRegister.getTopFrame().operandStack.push(Integer.valueOf(getConstVal(instruction)));
    }

    private String getConstVal(Instruction instruction) {
        return instruction.getDesc().split("_")[1];
    }

    private String getClassNameFromInvokeInstruction(Instruction instruction, ConstantPool constantPool) {
        int methodIndex = InstructionCp2.class.cast(instruction).getTargetMethodIndex();
        ConstantMethodrefInfo methodrefInfo = constantPool.getMethodrefInfo(methodIndex);
        ConstantClassInfo classInfo = methodrefInfo.getClassInfo(constantPool);
        return constantPool.getUtf8String(classInfo.getNameIndex());
    }

    private String getMethodNameFromInvokeInstruction(Instruction instruction, ConstantPool constantPool) {
        int methodIndex = InstructionCp2.class.cast(instruction).getTargetMethodIndex();
        ConstantMethodrefInfo methodrefInfo = constantPool.getMethodrefInfo(methodIndex);
        ConstantClassInfo classInfo = methodrefInfo.getClassInfo(constantPool);
        return methodrefInfo.getMethodNameAndType(constantPool).getName(constantPool);
    }

    private ClassFile loadClassFromClassPath(String fqcn) {
        return Stream.of(classPathEntries)
                .map(entry -> tryLoad(entry, fqcn))
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new RuntimeException(new ClassNotFoundException(fqcn)));
    }

    private ClassFile tryLoad(String entry, String fqcn) {
        try {
            byte[] bytes = Files.readAllBytes(new File(entry, fqcn.replace('.', '/') + ".class").toPath());
            return new ClassFileParser().parse(bytes);
        } catch (IOException e) {
            return null;
        }
    }

    static class PCRegister {
        Stack<StackFrame> methodStack;

        public PCRegister(Stack<StackFrame> methodStack) {
            this.methodStack = methodStack;
        }

        public StackFrame getTopFrame() {
            return methodStack.peek();
        }

        public ConstantPool getTopFrameClassConstantPool() {
            return getTopFrame().getClassFile().getConstantPool();
        }

        public Instruction getNextInstruction() {
            if (methodStack.isEmpty()) {
                return null;
            } else {
                StackFrame frameAtTop = methodStack.peek();
                return frameAtTop.getNextInstruction();
            }
        }

        public void popFrameFromMethodStack() {
            methodStack.pop();
        }
    }

    static class StackFrame {
        Object[] localVariables;
        Stack<Object> operandStack = new Stack<>();
        MethodInfo methodInfo;
        ClassFile classFile;

        int currentInstructionIndex;

        public Instruction getNextInstruction() {
            return methodInfo.getCode().get(currentInstructionIndex++);
        }

        public ClassFile getClassFile() {
            return classFile;
        }

        public StackFrame(Object[] localVariables, MethodInfo methodInfo, ClassFile classFile) {
            this.localVariables = localVariables;
            this.methodInfo = methodInfo;
            this.classFile = classFile;
        }

        public void pushObjectToOperandStack(Object object) {
            operandStack.push(object);
        }

        public Object popFromOperandStack() {
            return operandStack.pop();
        }

        public void setCurrentInstructionIndexByIfne(Instruction ifneInstruction) {
            String ifneDesc = ifneInstruction.getDesc();
            String[] descArr = ifneDesc.split(" ");
            int target = Integer.parseInt(descArr[descArr.length - 1]);
            List<Instruction> instructions = methodInfo.getCode();
            for (Instruction instruction : instructions) {
                if (instruction.getPc() == target) {
                    currentInstructionIndex = instructions.indexOf(instruction);
                    break;
                }
            }
        }
    }
}
