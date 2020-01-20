package com.github.hcsp;

import com.github.zxh.classpy.classfile.ClassFile;
import com.github.zxh.classpy.classfile.ClassFileParser;
import com.github.zxh.classpy.classfile.MethodInfo;
import com.github.zxh.classpy.classfile.bytecode.Bipush;
import com.github.zxh.classpy.classfile.bytecode.Instruction;
import com.github.zxh.classpy.classfile.bytecode.InstructionCp2;
import com.github.zxh.classpy.classfile.bytecode.Sipush;
import com.github.zxh.classpy.classfile.constant.*;
import com.github.zxh.classpy.common.FilePart;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;
import java.util.Stack;
import java.util.function.BinaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
                    int fieldIndex = ((InstructionCp2) instruction).getTargetFieldIndex();
                    ConstantPool constantPool = pcRegister.getTopFrameClassConstantPool();
                    ConstantFieldrefInfo fieldRefInfo = constantPool.getFieldrefInfo(fieldIndex);
                    ConstantClassInfo classInfo = fieldRefInfo.getClassInfo(constantPool);
                    ConstantNameAndTypeInfo nameAndTypeInfo = fieldRefInfo.getFieldNameAndTypeInfo(constantPool);

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
                    Object[] localVariables = getLocalVariablesForNewFrame(pcRegister, targetMethodInfo);
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
                case invokevirtual: {
                    String className = getClassNameFromInvokeInstruction(instruction, pcRegister.getTopFrameClassConstantPool());
                    String methodName = getMethodNameFromInvokeInstruction(instruction, pcRegister.getTopFrameClassConstantPool());
                    if ("java/io/PrintStream".equals(className) && "println".equals(methodName)) {
                        Object param = pcRegister.getTopFrame().popFromOperandStack();
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
                    pcRegister.getTopFrame().pushObjectToOperandStack(pcRegister.getTopFrame().localVariables[0]);
                    break;
                case iconst_1:
                case iconst_2:
                case iconst_3:
                case iconst_4:
                case iconst_5:
                    int constValue = Integer.parseInt(instruction.getDesc().split("_")[1]);
                    pcRegister.getTopFrame().pushObjectToOperandStack(constValue);
                    break;
                case irem:
                    caculate(pcRegister, (a, b) -> a % b);
                    break;
                case iadd:
                    caculate(pcRegister, Integer::sum);
                    break;
                case isub:
                    caculate(pcRegister, (a, b) -> a - b);
                    break;
                case imul:
                    caculate(pcRegister, (a, b) -> a * b);
                    break;
                case idiv:
                    caculate(pcRegister, (a, b) -> a / b);
                    break;
                case ifne: {
                    if ((Integer) pcRegister.getTopFrame().popFromOperandStack() != 0) {
                        pcRegister.getTopFrame().jumpToAimInstruction(instruction);
                    }
                }
                break;
                default:
                    throw new IllegalStateException("Opcode " + instruction + " not implemented yet!");
            }
        }
    }

    private void caculate(PCRegister pcRegister, BinaryOperator<Integer> operator) {
        Integer operand1 = (Integer) pcRegister.getTopFrame().popFromOperandStack();
        Integer operand2 = (Integer) pcRegister.getTopFrame().popFromOperandStack();
        pcRegister.getTopFrame().pushObjectToOperandStack(operator.apply(operand2, operand1));
    }

    private Object[] getLocalVariablesForNewFrame(PCRegister pcRegister, MethodInfo targetMethodInfo) {
        int paramNumber = MethodInfoUtil.getMethodParamNumber(targetMethodInfo);
        int localVariableIndex = paramNumber;
        Object[] localVariables = new Object[paramNumber];
        // 从操作数栈上弹出对应数量的参数放在新栈帧的局部变量表中
        while (localVariableIndex > 0) {
            localVariables[localVariableIndex - 1] = pcRegister.getTopFrame().popFromOperandStack();
            localVariableIndex--;
        }
        return localVariables;
    }


    private String getClassNameFromInvokeInstruction(Instruction instruction, ConstantPool constantPool) {
        int methodIndex = ((InstructionCp2) instruction).getTargetMethodIndex();
        ConstantMethodrefInfo methodRefInfo = constantPool.getMethodrefInfo(methodIndex);
        ConstantClassInfo classInfo = methodRefInfo.getClassInfo(constantPool);
        return constantPool.getUtf8String(classInfo.getNameIndex());
    }

    private String getMethodNameFromInvokeInstruction(Instruction instruction, ConstantPool constantPool) {
        int methodIndex = ((InstructionCp2) instruction).getTargetMethodIndex();
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

    static class MethodInfoUtil {
        static Pattern IN_PARENTHESIS_PATTERN = Pattern.compile("(?<=\\().*?(?=\\))");

        static int getMethodParamNumber(MethodInfo targetMethodInfo) {
            String descriptorIndex = targetMethodInfo.getParts()
                    .stream()
                    .filter(x -> x.getName().equals("descriptor_index"))
                    .findFirst()
                    .map(FilePart::getDesc).orElse("");

            return getParamNumber(descriptorIndex);
        }

        private static int getParamNumber(String descriptorIndex) {
            // 获取括号内的参数个数 #19->(IIIIII)I => IIIIII ,取IIIIII的长度
            Matcher matcher = IN_PARENTHESIS_PATTERN.matcher(descriptorIndex);
            while (matcher.find()) {
                return matcher.group().length();
            }
            return 0;
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
            }
            StackFrame frameAtTop = methodStack.peek();
            return frameAtTop.getNextInstruction();
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

        public void jumpToAimInstruction(Instruction ifConditionInstruction) {
            List<Instruction> instructions = methodInfo.getCode();
            String[] descArr = ifConditionInstruction.getDesc().split(" ");
            int aimLineNumber = Integer.parseInt(descArr[descArr.length - 1]);
            Instruction aimInstruction = instructions
                    .stream()
                    .filter(x -> x.getPc() == aimLineNumber)
                    .findFirst()
                    .orElseThrow(RuntimeException::new);

            currentInstructionIndex = instructions.indexOf(aimInstruction);
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
    }
}
