package com.github.hcsp;

import com.github.zxh.classpy.classfile.ClassFile;
import com.github.zxh.classpy.classfile.ClassFileParser;
import com.github.zxh.classpy.classfile.MethodInfo;
import com.github.zxh.classpy.classfile.bytecode.Bipush;
import com.github.zxh.classpy.classfile.bytecode.Instruction;
import com.github.zxh.classpy.classfile.bytecode.InstructionCp2;
import com.github.zxh.classpy.classfile.constant.*;

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
        new MiniJVM("target/classes", "com.github.hcsp.BranchClass").start();
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

                    //应该分析方法的参数，从操作数栈上弹出对应数量的参数放在新栈帧的局部变量表中
                    Object[] localVariables = new Object[targetMethodInfo.getMaxLocals()];
                    if (targetMethodInfo.getMaxLocals() > 0) {
                        for (int i = 0; i < targetMethodInfo.getMaxLocals(); i++) {
                            //把栈顶的栈桢，从操作数栈上弹出对应数量的参数，添加到新栈桢里的局部变量表
                            localVariables[i] = pcRegister.getTopFrame().popFromOperandStack();
                        }
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
                case iload_0: {  //从当前栈桢局部变量表中0号位置得到int类型数据，加载到操作数栈
                    Object intValue = pcRegister.getTopFrame().getLocalVariables()[0];
                    pcRegister.getTopFrame().pushObjectToOperandStack(intValue);
                }
                break;
                case iconst_1: {//把常量1添加到操作数栈
                    pcRegister.getTopFrame().pushObjectToOperandStack(1);
                }
                break;
                case iconst_2: { //把常量2添加到操作数栈
                    pcRegister.getTopFrame().pushObjectToOperandStack(2);
                }
                break;
                case iconst_3: { //把常量3添加到操作数栈
                    pcRegister.getTopFrame().pushObjectToOperandStack(3);
                }
                break;
                case iconst_4: { //把常量4添加到操作数栈
                    pcRegister.getTopFrame().pushObjectToOperandStack(4);
                }
                break;
                case iconst_5: { //把常量5添加到操作数栈
                    pcRegister.getTopFrame().pushObjectToOperandStack(5);
                }
                break;
                case irem: { //value1和value2都必须是int类型。这些值是从操作数堆栈中弹出的。int结果是value1-（value1/value2）*value2。结果被推送到操作数堆栈上。
                    int value2 = (int) pcRegister.getTopFrame().popFromOperandStack();
                    int value1 = (int) pcRegister.getTopFrame().popFromOperandStack();
                    int result = value1 - (value1 / value2) * value2;
                    pcRegister.getTopFrame().pushObjectToOperandStack(result);
                }
                break;
                case ifne: { //当且仅当值≠0时，ifne成功
                    int value = (int) pcRegister.getTopFrame().popFromOperandStack();
                    if (value != 0) {
                        //得到当前栈桢里执行的指令
                        List<Instruction> stackInstructionList = pcRegister.getTopFrame().getMethodInfo().getCode();
                        //从instruction.getDesc获取到欲调转的指令号,使用空格进行分割
                        int pc = Integer.valueOf(instruction.getDesc().split(" ")[1]);
                        for (int i = 0; i < stackInstructionList.size(); i++) {
                            if (pc == stackInstructionList.get(i).getPc()) {
                                pcRegister.getTopFrame().setCurrentInstructionIndex(i);
                                break;
                            }
                        }
                    }
                }
                break;
                case sipush: { //立即无符号字节1和字节2的值被组合成一个中间短字节，其中短字节的值为（字节1<<8）|字节2。然后将中间值符号扩展为整型值。该值被推送到操作数堆栈上。
                    Integer returnValue = Integer.valueOf(instruction.getDesc().split(" ")[1]);
                    pcRegister.getTopFrame().pushObjectToOperandStack(returnValue);
                }
                break;
                case isub: { //value1和value2都必须是int类型。这些值是从操作数堆栈中弹出的。int结果是value1-value2。结果被推送到操作数堆栈上。
                    int value2 = (int) pcRegister.getTopFrame().popFromOperandStack();
                    int value1 = (int) pcRegister.getTopFrame().popFromOperandStack();
                    int result = value1 - value2;
                    pcRegister.getTopFrame().pushObjectToOperandStack(result);
                }
                break;
                case imul:{ //value1和value2都必须是int类型。这些值来自操作数堆栈。int结果是value1*value2。结果被推送到操作数堆栈上。
                    int value2 = (int) pcRegister.getTopFrame().popFromOperandStack();
                    int value1 = (int) pcRegister.getTopFrame().popFromOperandStack();
                    int result = value1 * value2;
                    pcRegister.getTopFrame().pushObjectToOperandStack(result);
                }
                break;
                case _return:
                    pcRegister.popFrameFromMethodStack();
                    break;
                default:
                    throw new IllegalStateException("Opcode " + instruction + " not implemented yet!");
            }
        }
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

        /**
         * get set
         */
        public Object[] getLocalVariables() {
            return localVariables;
        }

        public void setLocalVariables(Object[] localVariables) {
            this.localVariables = localVariables;
        }

        public Stack<Object> getOperandStack() {
            return operandStack;
        }

        public void setOperandStack(Stack<Object> operandStack) {
            this.operandStack = operandStack;
        }

        public MethodInfo getMethodInfo() {
            return methodInfo;
        }

        public void setMethodInfo(MethodInfo methodInfo) {
            this.methodInfo = methodInfo;
        }

        public void setClassFile(ClassFile classFile) {
            this.classFile = classFile;
        }

        public int getCurrentInstructionIndex() {
            return currentInstructionIndex;
        }

        public void setCurrentInstructionIndex(int currentInstructionIndex) {
            this.currentInstructionIndex = currentInstructionIndex;
        }
    }
}
