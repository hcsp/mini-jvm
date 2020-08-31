package com.github.hcsp;

import com.github.zxh.classpy.classfile.ClassFile;
import com.github.zxh.classpy.classfile.ClassFileParser;
import com.github.zxh.classpy.classfile.MethodInfo;
import com.github.zxh.classpy.classfile.bytecode.*;
import com.github.zxh.classpy.classfile.constant.ConstantClassInfo;
import com.github.zxh.classpy.classfile.constant.ConstantFieldrefInfo;
import com.github.zxh.classpy.classfile.constant.ConstantMethodrefInfo;
import com.github.zxh.classpy.classfile.constant.ConstantNameAndTypeInfo;
import com.github.zxh.classpy.classfile.constant.ConstantPool;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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
//        new MiniJVM("target/classes", "com.github.hcsp.SimpleClass").start();
//        new MiniJVM("target/classes", "com.github.hcsp.BranchClass").start();
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
        //从classpath中加载要运行类的class文件
        ClassFile mainClassFile = loadClassFromClassPath(mainClass);
        //从class文件中找到main方法
        MethodInfo methodInfo = mainClassFile.getMethod("main").get(0);
        //创建方法栈
        Stack<StackFrame> methodStack = new Stack<>();
        //创建局部变量表，将传递给main方法的参数依次放到局部变量表中
        Object[] localVariablesForMainStackFrame = new Object[methodInfo.getMaxStack()];
        localVariablesForMainStackFrame[0] = null;
        //将main方法进栈
        methodStack.push(new StackFrame(localVariablesForMainStackFrame, methodInfo, mainClassFile));
        //创建程序计数器
        PCRegister pcRegister = new PCRegister(methodStack);

        while (true) {
            //从程序计数器中取出下一条指令
            Instruction instruction = pcRegister.getNextInstruction();
            if (instruction == null) {
                //没有下一条指令，退出程序
                break;
            }
            //根据指令选择对应操作
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
                    int length = targetMethodInfo.getMaxLocals();
                    Object[] localVariables = new Object[length];
                    for (int i = length - 1; i >= 0; i--) {
                        localVariables[i] = pcRegister.getTopFrame().popFromOperandStack();
                    }
                    // TODO 应该分析方法的参数，从操作数栈上弹出对应数量的参数放在新栈帧的局部变量表中
                    StackFrame newFrame = new StackFrame(localVariables, targetMethodInfo, classFile);
                    methodStack.push(newFrame);
                }
                break;
                case bipush: {
                    Bipush bipush = (Bipush) instruction;
                    pcRegister.getTopFrame().pushObjectToOperandStack(bipush.getOperand());
                }
                break;
                case sipush: {
                    Sipush sipush = (Sipush) instruction;
                    pcRegister.getTopFrame().pushObjectToOperandStack(sipush.getOperand());

                }
                break;
                case ireturn: {
                    Object returnValue = pcRegister.getTopFrame().popFromOperandStack();
                    pcRegister.popFrameFromMethodStack();
                    pcRegister.getTopFrame().pushObjectToOperandStack(returnValue);
                }
                break;
                case iload_0: {
                    Object obj = pcRegister.getTopFrame().localVariables[0];
                    if (obj instanceof Integer) {
                        pcRegister.getTopFrame().pushObjectToOperandStack(obj);
                    } else {
                        throw new IllegalArgumentException(obj + " is not int type");
                    }

                }
                break;
                case iconst_1:
                case iconst_2:
                case iconst_3:
                case iconst_4:
                case iconst_5: {
                    int num = instruction.getOperand();
                    pcRegister.getTopFrame().pushObjectToOperandStack(num);
                }
                break;
                case irem: {
                    int value2 = (int) pcRegister.getTopFrame().popFromOperandStack();
                    int value1 = (int) pcRegister.getTopFrame().popFromOperandStack();
                    int result = value1 - (value1 / value2) * value2;
                    pcRegister.getTopFrame().pushObjectToOperandStack(result);
                }
                break;
                case isub: {
                    int value2 = (int) pcRegister.getTopFrame().popFromOperandStack();
                    int value1 = (int) pcRegister.getTopFrame().popFromOperandStack();
                    int result = value1 - value2;
                    pcRegister.getTopFrame().pushObjectToOperandStack(result);
                }
                break;
                case imul: {
                    int value2 = (int) pcRegister.getTopFrame().popFromOperandStack();
                    int value1 = (int) pcRegister.getTopFrame().popFromOperandStack();
                    int result = value1 * value2;
                    pcRegister.getTopFrame().pushObjectToOperandStack(result);
                }
                break;
                case ifne: {
                    if ((int) pcRegister.getTopFrame().popFromOperandStack() != 0) {
                        pcRegister.getTopFrame().offsetNextInstruction(2);
                    }
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

    /**
     * 栈针
     */
    static class StackFrame {
        //局部变量表
        Object[] localVariables;
        //操作树栈
        Stack<Object> operandStack = new Stack<>();
        //方法信息
        MethodInfo methodInfo;
        //类的字节码文件
        ClassFile classFile;
        //当前指令执行在哪一行
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

        //进栈
        public void pushObjectToOperandStack(Object object) {
            operandStack.push(object);
        }

        //出栈
        public Object popFromOperandStack() {
            return operandStack.pop();
        }

        public void offsetNextInstruction(int offset) {
            currentInstructionIndex += offset;
        }
    }
}
