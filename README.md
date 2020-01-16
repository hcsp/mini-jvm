# 让我们来实现一个JVM！

在课程中，我们已经实现了一个简单的JVM的执行引擎：

- 从classpath中加载对应的类的字节码；
- 解析该字节码并执行`main`方法；
- 通过`invokestatic`指令调用其他方法。

我希望你能实现更多的虚拟机指令：分支跳转、取余、乘法等。

请补全`MiniJVM`的实现，使之能够运行`BranchClass`和`RecursiveClass`。祝你好运！