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
        new MiniJVM("target/classes", "com.github.hcsp.SimpleClass").start();
        new MiniJVM("target/classes", "com.github.hcsp.BranchClass").start();
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

        MethodInfo mainMethodInfo = mainClassFile.getMethod("main").get(0);

        Stack<StackFrame> mainMethodStack = new Stack<>();

        Object[] localVariablesForMainStackFrame = new Object[mainMethodInfo.getMaxStack()];
        localVariablesForMainStackFrame[0] = null;

        mainMethodStack.push(new StackFrame(localVariablesForMainStackFrame, mainMethodInfo, mainClassFile));

        PCRegister pcRegister = new PCRegister(mainMethodStack);

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

                    //targetMethodInfo.getMaxLocals()用于获取实际传参的长度
                    Object[] localVariables = new Object[targetMethodInfo.getMaxLocals()];
                    //targetMethodInfo.getParts().get(2).getDesc()
                    if (targetMethodInfo.getMaxLocals() > 0) {
                        //分析方法的参数,从操作数栈上弹出对应数量的参数放在新栈帧的局部变量表中
                        for (int i = 0; i < targetMethodInfo.getMaxLocals(); i++) {
                            Object param = pcRegister.getTopFrame().popFromOperandStack();//从栈上弹出一个需要的变量值
                            localVariables[i] = param;
                        }
                    }

                    StackFrame newFrame = new StackFrame(localVariables, targetMethodInfo, classFile);
                    mainMethodStack.push(newFrame);
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
                case iload_0: {//从当前栈帧局部变量表中0号位置取int类型的数值加载到操作数栈
                    StackFrame topFrame = pcRegister.getTopFrame();
                    Object intValue = topFrame.getLocalVariables()[0];
                    /*if (Integer.class.isInstance(intValue)) {
                    //暂且忽略类型检查
                        throw new IllegalStateException(intValue + " is not int value!");
                    }*/
                    topFrame.pushObjectToOperandStack(intValue);
                }
                break;
                case ldc://将一个常量加载到操作数栈
                    System.out.println();
                    break;
                case aconst_null://将null加载到操作数栈
                    System.out.println();
                    break;
                case iconst_2: {//将变量2压入操作数栈
                    pcRegister.getTopFrame().pushObjectToOperandStack(2);
                }
                break;
                case irem: {//value1和value2都必须为int类型。 从操作数堆栈中弹出值。 int结果为value1-（value1 / value2）* value2。 结果被压入操作数堆栈。
                    StackFrame topFrame = pcRegister.getTopFrame();
                    Integer value2 = (Integer) topFrame.popFromOperandStack();
                    Integer value1 = (Integer) topFrame.popFromOperandStack();
                    Integer result = value1 - (value1 / value2) * value2;
                    topFrame.pushObjectToOperandStack(result);
                }
                break;
                case ifne: {//if条件分支
                    StackFrame topFrame = pcRegister.getTopFrame();
                    MethodInfo methodInfo = topFrame.getMethodInfo();
                    List<Instruction> code = methodInfo.getCode();
//                    List<FilePart> parts = methodInfo.getParts();
                    int ifParam = (int) topFrame.popFromOperandStack();

                    //此处相等执行下一条指令即可,若不等则调转到else对应的指令位置
                    if (ifParam != 0) {//todo ifne指令的比较值固定是0么?
                        //从instruction.getDesc获取到欲调转的指令号,使用空格进行分割
                        int pc = Integer.parseInt(instruction.getDesc().split(" ")[1]);
                        for (int i = 0; i < code.size(); i++) {
                            if (pc == code.get(i).getPc()) {
                                topFrame.setCurrentInstructionIndex(i);
                                break;
                            }
                        }
                    }
                }
                break;
                case sipush: {//无符号立即数byte1和byte2的值组合成一个中间short，其中short的值为（byte1 << 8）| 字节2。 然后将中间值符号扩展为int值。 该值被压入操作数堆栈。
                    pcRegister.popFrameFromMethodStack();
                    StackFrame topFrame = pcRegister.getTopFrame();
                    Integer returnValue = Integer.parseInt(instruction.getDesc().split(" ")[1]);
                    topFrame.pushObjectToOperandStack(returnValue);
                }
                break;
                case iconst_5: {
                    pcRegister.getTopFrame().pushObjectToOperandStack(5);
                }
                break;
                case iconst_1: {//将常量1加载到操作数栈
                    pcRegister.getTopFrame().pushObjectToOperandStack(1);
                }
                break;
                case isub: {//value1和value2都必须为int类型。 从操作数堆栈中弹出值。 int结果为value1-value2。 结果被压入操作数堆栈
                    StackFrame topFrame = pcRegister.getTopFrame();
                    Integer value2 = (Integer) topFrame.popFromOperandStack();
                    Integer value1 = (Integer) topFrame.popFromOperandStack();
                    int result = value1 - value2;
                    topFrame.pushObjectToOperandStack(result);
                }
                break;
                case imul: {//value1和value2都必须为int类型。 从操作数堆栈中弹出值。 int结果为value1 * value2。 结果被压入操作数堆栈。
                    StackFrame topFrame = pcRegister.getTopFrame();
                    Integer value2 = (Integer) topFrame.popFromOperandStack();
                    Integer value1 = (Integer) topFrame.popFromOperandStack();
                    int result = value1 * value2;
                    topFrame.pushObjectToOperandStack(result);
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

        public Object[] getLocalVariables() {
            return localVariables;
        }

        public MethodInfo getMethodInfo() {
            return methodInfo;
        }

        public void setCurrentInstructionIndex(int currentInstructionIndex) {
            this.currentInstructionIndex = currentInstructionIndex;
        }
    }
}
