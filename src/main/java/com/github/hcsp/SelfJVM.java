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
import java.util.Objects;
import java.util.Stack;
import java.util.stream.Stream;

/**
 * 自己的虚拟机
 */
public class SelfJVM {

    private String mainClass;
    private String[] classPathEntries;

    public static void main(String[] args) {
        new SelfJVM("target/classes", "com.github.hcsp.SimpleClass").start();
    }

    /**
     * 创建虚拟机，使用指定的classPath和mainCLass
     *
     * @param classPath
     * @param mainClass
     */
    public SelfJVM(String classPath, String mainClass) {
        this.mainClass = mainClass;
        this.classPathEntries = classPath.split(File.pathSeparator);
    }

    /**
     * 启动并运行虚拟机
     */
    public void start() {
        //加载主类
        ClassFile mainClassFile = loadClassFromClassPath(mainClass);
        MethodInfo methodInfo = mainClassFile.getMethod("main").get(0);
        //创建方法栈
        Stack<StackFrame> methodStack = new Stack<>();
        //创建局部变量表
        Object[] localVariablesForMainStackFrame = new Object[methodInfo.getMaxStack()];
        localVariablesForMainStackFrame[0] = null;
        //方法栈添加栈帧
        methodStack.push(new StackFrame(localVariablesForMainStackFrame, methodInfo, mainClassFile));
        //创建程序计数器
        PCRegister pcRegister = new PCRegister(methodStack);
        while (true) {
            //得到下一个指令
            Instruction instruction = pcRegister.getNextInstruction();
            if (instruction == null) {
                break;
            }
            //常量池
            switch (instruction.getOpcode()) {
                case getstatic: {
                    ConstantPool constantPool = pcRegister.getTopFrameClassConstantPool();
                    int fieldIndex = InstructionCp2.class.cast(instruction).getTargetFieldIndex();
                    ConstantFieldrefInfo fieldrefInfo = constantPool.getFieldrefInfo(fieldIndex);
                    ConstantClassInfo classInfo = fieldrefInfo.getClassInfo(constantPool);
                    ConstantNameAndTypeInfo nameAndTypeInfo = fieldrefInfo.getFieldNameAndTypeInfo(constantPool);

                    String className = constantPool.getUtf8String(classInfo.getNameIndex());
                    String fieldName = nameAndTypeInfo.getName(constantPool);
                    String fieldType = nameAndTypeInfo.getType(constantPool);

                    if ("java/lang/System".equals(className) && "out".equals(fieldName)) {
                        Object field = System.out;
                        pcRegister.getTopFrame().pushObjectToOperandStack(field);
                    } else {
                        throw new IllegalStateException("Not implemented yet!");
                    }
                    break;
                }
                case invokestatic: {
                    //
                    String className = getClassNameFromInvokeInstruction(pcRegister, instruction);
                    String methodName = getMethodNameFromInvokeInstruction(pcRegister, instruction);
                    ClassFile classFile = loadClassFromClassPath(className);
                    MethodInfo targetMethodInfo = classFile.getMethod(methodName).get(0);

                    Object[] localVariables = new Object[targetMethodInfo.getMaxLocals()];
                    //TODO 应该分析方法的参数，从操作数栈上弹出对应数量的参数放在新栈桢的局部变量表中
                    StackFrame newFrame = new StackFrame(localVariables, targetMethodInfo, classFile);
                    methodStack.push(newFrame);
                    break;
                }
                case bipush: {
                    Bipush bipush = (Bipush) instruction;
                    pcRegister.getTopFrame().pushObjectToOperandStack(bipush.getOperand());
                    break;
                }
                case ireturn: {
                    Object returnValue = pcRegister.getTopFrame().popFromOperandStack();
                    pcRegister.popFrameFromMethodStack();
                    pcRegister.getTopFrame().pushObjectToOperandStack(returnValue);
                    break;
                }
                case invokevirtual: {
                    String className = getClassNameFromInvokeInstruction(pcRegister, instruction);
                    String methodName = getMethodNameFromInvokeInstruction(pcRegister, instruction);
                    if ("java/io/PrintStream".equals(className) && "println".equals(methodName)) {
                        Object param = pcRegister.getTopFrame().popFromOperandStack();
                        Object thisObject = pcRegister.getTopFrame().popFromOperandStack();
                        System.out.println(param);
                    } else {
                        throw new IllegalStateException("Not implemented yet!");
                    }
                    break;
                }
                case _return: {
                    pcRegister.popFrameFromMethodStack();
                    break;
                }
                default:
                    throw new IllegalStateException("Opcode " + instruction + " not implemented yet!");
            }
        }
    }

    private String getClassNameFromInvokeInstruction(PCRegister pcRegister, Instruction instruction) {
        ConstantPool constantPool = pcRegister.getTopFrameClassConstantPool();
        int methodIndex = InstructionCp2.class.cast(instruction).getTargetMethodIndex();
        ConstantMethodrefInfo methodrefInfo = constantPool.getMethodrefInfo(methodIndex);
        ConstantClassInfo classInfo = methodrefInfo.getClassInfo(constantPool);
        return constantPool.getUtf8String(classInfo.getNameIndex());
    }

    private String getMethodNameFromInvokeInstruction(PCRegister pcRegister, Instruction instruction) {
        ConstantPool constantPool = pcRegister.getTopFrameClassConstantPool();
        int methodIndex = InstructionCp2.class.cast(instruction).getTargetMethodIndex();
        ConstantMethodrefInfo methodrefInfo = constantPool.getMethodrefInfo(methodIndex);
        return methodrefInfo.getMethodNameAndType(constantPool).getName(constantPool);
    }

    /**
     * @param fqcn Fully-Qualified Class Name
     */
    private ClassFile loadClassFromClassPath(String fqcn) {
        return Stream.of(classPathEntries)
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

    /**
     * 程序计数器
     */
    class PCRegister {
        Stack<StackFrame> methodStack;

        public PCRegister(Stack<StackFrame> methodStack) {
            this.methodStack = methodStack;
        }

        public StackFrame getTopFrame() {
            return methodStack.peek();
        }

        public ConstantPool getTopFrameClassConstantPool() {
            return getTopFrame().classFile.getConstantPool();
        }

        public Instruction getNextInstruction() {
            if (methodStack.isEmpty()) {
                return null;
            }
            StackFrame frameAtTop = methodStack.peek();
            return frameAtTop.getNextInstruction();
        }

        public StackFrame popFrameFromMethodStack() {
            return methodStack.pop();
        }
    }

    /**
     * 栈帧
     */
    class StackFrame {
        /**
         * 局部变量表
         */
        Object[] localVariables;
        /**
         * 操作数栈
         */
        Stack<Object> operandStack = new Stack<>();
        /**
         * 方法信息
         */
        MethodInfo methodInfo;
        /**
         * 当前指令执行的位置
         */
        int currentInstructionIndex;
        /**
         * 类
         */
        ClassFile classFile;

        public StackFrame(Object[] localVariables, MethodInfo methodInfo, ClassFile classFile) {
            this.localVariables = localVariables;
            this.methodInfo = methodInfo;
            this.classFile = classFile;
        }

        public Instruction getNextInstruction() {
            return methodInfo.getCode().get(currentInstructionIndex++);
        }

        public void pushObjectToOperandStack(Object object) {
            operandStack.push(object);
        }

        public Object popFromOperandStack() {
            return operandStack.pop();
        }
    }
}
