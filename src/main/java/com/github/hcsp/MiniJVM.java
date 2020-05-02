package com.github.hcsp;


import com.github.zxh.classpy.classfile.ClassFile;
import com.github.zxh.classpy.classfile.ClassFileParser;
import com.github.zxh.classpy.classfile.MethodInfo;
import com.github.zxh.classpy.classfile.bytecode.Bipush;
import com.github.zxh.classpy.classfile.bytecode.Instruction;
import com.github.zxh.classpy.classfile.bytecode.InstructionCp2;
import com.github.zxh.classpy.classfile.bytecode.Sipush;
import com.github.zxh.classpy.classfile.constant.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Stack;

/**
 * 这是一个用来学习的JVM
 */
public class MiniJVM {
    private String mainClass;
    //-classpath /home/xxx:/home/xxx
    private String[] classPathEntries;

    public static void main(String[] args) {
        new MiniJVM("target/classes", "com.github.hcsp.RecursiveClass").start();
    }

    /**
     * 创建一个迷你JVM 使用指定的classpath和mainClass
     * @param mainClass
     * @param classPath 启动时的classPath
     */
    public MiniJVM(String classPath, String mainClass) {
        this.mainClass = mainClass;
        this.classPathEntries = classPath.split(File.pathSeparator);
    }

    /**
     * 启动运行虚拟机
     */
    public void start() {
        ClassFile mainClassFile = loadClassFromClassPath(mainClass);
        MethodInfo methodInfo = mainClassFile.getMethod("main").get(0);
        Stack<StackFrame> methodStack = new Stack<>();
        Object[] localVariablesForMainStackFrame = new Object[methodInfo.getMaxStack()];
        localVariablesForMainStackFrame[0] = null;
        StackFrame mainStackFrame = new StackFrame(localVariablesForMainStackFrame, methodInfo);
        methodStack.push(mainStackFrame);
        PCRegister pcRegister = new PCRegister(methodStack);
        ConstantPool constantPool = mainClassFile.getConstantPool();
        while (true) {
            Instruction instruction = pcRegister.getNextInstruction();
            if (instruction == null) {
                break;
            }
            switch (instruction.getOpcode()) {
                case imul:{
                    Integer object1 = (int) pcRegister.getTopStackFrame().popFromOperandStack();
                    Integer object2 = (int) pcRegister.getTopStackFrame().popFromOperandStack();
                    Integer object = object2 * object1;
                    pcRegister.getTopStackFrame().pushObjectToOperandStack(object);
                }
                break;
                case isub:{
                    Integer object1 = (int) pcRegister.getTopStackFrame().popFromOperandStack();
                    Integer object2 = (int) pcRegister.getTopStackFrame().popFromOperandStack();
                    Integer object = object2 - object1;
                    pcRegister.getTopStackFrame().pushObjectToOperandStack(object);
                }
                break;
                case sipush: {
                    Sipush sipush = (Sipush) instruction;
                    pcRegister.getTopStackFrame().pushObjectToOperandStack(sipush.getOperand());
                }
                break;
                case ifne: {
                    Integer object = (int) pcRegister.getTopStackFrame().popFromOperandStack();
                    if (object != 0) {
                        pcRegister.getTopStackFrame().jumpToAimInstruction(instruction);
                    }
                }
                break;
                case irem: {
                    Integer object1 = (int) pcRegister.getTopStackFrame().popFromOperandStack();
                    Integer object2 = (int) pcRegister.getTopStackFrame().popFromOperandStack();
                    Integer object = object2 % object1;
                    pcRegister.getTopStackFrame().pushObjectToOperandStack(object);
                }
                break;
                case iconst_1: {
                    pcRegister.getTopStackFrame().pushObjectToOperandStack(1);
                }
                break;
                case iconst_2: {
                    pcRegister.getTopStackFrame().pushObjectToOperandStack(2);
                }
                break;
                case iconst_3: {
                    pcRegister.getTopStackFrame().pushObjectToOperandStack(3);
                }
                break;
                case iconst_4: {
                    pcRegister.getTopStackFrame().pushObjectToOperandStack(4);
                }
                break;
                case iconst_5: {
                    pcRegister.getTopStackFrame().pushObjectToOperandStack(5);
                }
                break;
                case iload_0: {
                    Object value = pcRegister.getTopStackFrame().getLocalVariables()[0];
                    pcRegister.getTopStackFrame().pushObjectToOperandStack(value);
                }
                break;
                case getstatic: {
                    int fieldIndex = ((InstructionCp2) instruction).getTargetFieldIndex();
                    String className = getClassNameFromInvokeInstructionByField(fieldIndex, constantPool);
                    String fieldName = getFieldNameFromInvokeInstruction(fieldIndex, constantPool);
                    if ("java/lang/System".equals(className) && "out".equals(fieldName)) {
                        Object field = System.out;
                        pcRegister.getTopStackFrame().pushObjectToOperandStack(field);
                    } else {
                        throw new IllegalStateException("Not implemented yet!");
                    }
                }
                break;
                case invokestatic: {
                    int index = ((InstructionCp2) instruction).getTargetMethodIndex();
                    String className = getClassNameFromInvokeInstructionByMethod(index, constantPool);
                    String methodName = getMethodNameFromInvokeInstruction(index, constantPool);
                    ClassFile classFile = loadClassFromClassPath(className);
                    MethodInfo targetMethodInfo = classFile.getMethod(methodName).get(0);
                    Object[] localVariables = getLocalVariables(pcRegister, targetMethodInfo);
                    StackFrame newFrame = new StackFrame(localVariables, targetMethodInfo);
                    methodStack.push(newFrame);
                }
                break;
                case bipush: {
                    Bipush bipush = (Bipush) instruction;
                    pcRegister.getTopStackFrame().pushObjectToOperandStack(bipush.getOperand());
                }
                break;
                case ireturn: {
                    Object returnVal = pcRegister.getTopStackFrame().popFromOperandStack();
                    pcRegister.popFrameFromMethodStack();
                    pcRegister.getTopStackFrame().pushObjectToOperandStack(returnVal);
                }
                break;
                case invokevirtual: {
                    int index = ((InstructionCp2) instruction).getTargetMethodIndex();
                    String className = getClassNameFromInvokeInstructionByMethod(index, constantPool);
                    String methodName = getMethodNameFromInvokeInstruction(index, constantPool);
                    if ("java/io/PrintStream".equals(className) && "println".equals(methodName)) {
                        Object param = pcRegister.getTopStackFrame().popFromOperandStack();
                        pcRegister.getTopStackFrame().popFromOperandStack();//把this弹出
                        System.out.println(param);
                    } else {
                        throw new IllegalStateException("Not implemented yet!");
                    }
                }
                break;
                case _return: {
                    pcRegister.popFrameFromMethodStack();
                }
                break;
                default:
                    throw new IllegalStateException("Opcode " + instruction.getOpcode() + " not implemented yet!");
            }
        }
    }

    private Object[] getLocalVariables(PCRegister pcRegister, MethodInfo targetMethodInfo) {
        int localVariablesLength = targetMethodInfo.getMaxLocals();
        Object[] localVariables = new Object[localVariablesLength];
        for (int i = 0; i < localVariablesLength; i++) {
            localVariables[i] = pcRegister.getTopStackFrame().popFromOperandStack();
        }
        return localVariables;
    }

    private String getMethodNameFromInvokeInstruction(int index, ConstantPool constantPool) {
        ConstantMethodrefInfo methodrefInfo = constantPool.getMethodrefInfo(index);
        ConstantNameAndTypeInfo methodNameAndType = methodrefInfo.getMethodNameAndType(constantPool);
        return methodNameAndType.getName(constantPool);
    }

    private String getFieldNameFromInvokeInstruction(int index, ConstantPool constantPool) {
        ConstantFieldrefInfo fieldrefInfo = constantPool.getFieldrefInfo(index);
        ConstantNameAndTypeInfo fieldNameAndTypeInfo = fieldrefInfo.getFieldNameAndTypeInfo(constantPool);
        return fieldNameAndTypeInfo.getName(constantPool);
    }

    private String getClassNameFromInvokeInstructionByField(int index, ConstantPool constantPool) {
        ConstantFieldrefInfo fieldrefInfo = constantPool.getFieldrefInfo(index);
        ConstantClassInfo classInfo = fieldrefInfo.getClassInfo(constantPool);
        return constantPool.getUtf8String(classInfo.getNameIndex());
    }

    private String getClassNameFromInvokeInstructionByMethod(int index, ConstantPool constantPool) {
        ConstantMethodrefInfo methodrefInfo = constantPool.getMethodrefInfo(index);
        ConstantClassInfo classInfo = methodrefInfo.getClassInfo(constantPool);
        return constantPool.getUtf8String(classInfo.getNameIndex());
    }


    private ClassFile loadClassFromClassPath(String fqcn) {
        return Arrays.stream(classPathEntries)
                .map(entry -> tryLoad(entry, fqcn))
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new RuntimeException(new ClassNotFoundException(fqcn)));
    }

    private ClassFile tryLoad(String entry, String fqcn) {
        try {
            byte[] bytes = Files.readAllBytes(new File(entry, fqcn.replace(".", "/") + ".class").toPath());
            return new ClassFileParser().parse(bytes);
        } catch (IOException e) {
            return null;
        }
    }

    class PCRegister {
        private Stack<StackFrame> methodStack;

        public PCRegister(Stack<StackFrame> methodStack) {
            this.methodStack = methodStack;
        }

        public StackFrame getTopStackFrame() {
            return methodStack.peek();
        }

        private Instruction getNextInstruction() {
            if (methodStack.isEmpty()) {
                return null;
            } else {
                StackFrame topStackFrame = methodStack.peek();
                return topStackFrame.getNextInstruction();
            }
        }

        public void popFrameFromMethodStack() {
            methodStack.pop();
        }

    }

    class StackFrame {
        Object[] localVariables;
        Stack<Object> operandStack = new Stack<>();
        MethodInfo methodInfo;
        int curInstructionIndex;//当前字节码指令执行到哪里的索引

        public Instruction getNextInstruction() {
            return methodInfo.getCode().get(curInstructionIndex++);
        }

        public StackFrame(Object[] localVariables, MethodInfo methodInfo) {
            this.localVariables = localVariables;
            this.methodInfo = methodInfo;
        }

        public void pushObjectToOperandStack(Object object) {
            operandStack.push(object);
        }

        public Object popFromOperandStack() {
            return operandStack.pop();
        }

        public Object[] getLocalVariables() {
            return localVariables;
        }

        public void jumpToAimInstruction(Instruction instruction) {
            List<Instruction> instructions = methodInfo.getCode();
            String[] descArr = instruction.getDesc().split(" ");
            int aimLineNumber = Integer.parseInt(descArr[descArr.length - 1]);
            Instruction aimInstruction = instructions.stream()
                    .filter(item -> item.getPc() == aimLineNumber)
                    .findFirst()
                    .orElseThrow(RuntimeException::new);
            curInstructionIndex = instructions.indexOf(aimInstruction);
        }
    }
}
