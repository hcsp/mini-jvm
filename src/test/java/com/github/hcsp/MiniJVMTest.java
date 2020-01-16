package com.github.hcsp;

import com.github.blindpirate.extensions.CaptureSystemOutput;
import com.github.blindpirate.extensions.CaptureSystemOutputExtension;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.File;

@ExtendWith(CaptureSystemOutputExtension.class)
public class MiniJVMTest {
    private String classPath = new File("target/classes").getAbsolutePath();

    @Test
    @CaptureSystemOutput
    public void simpleClassTest(CaptureSystemOutput.OutputCapture capture) {
        capture.expect(Matchers.containsString("42"));
        new MiniJVM(classPath, "com.github.hcsp.SimpleClass").start();
    }

    @Test
    @CaptureSystemOutput
    public void branchTest(CaptureSystemOutput.OutputCapture capture) {
        capture.expect(Matchers.containsString("200"));
        new MiniJVM(classPath, "com.github.hcsp.BranchClass").start();
    }

    @Test
    @CaptureSystemOutput
    public void recursionTest(CaptureSystemOutput.OutputCapture capture) {
        capture.expect(Matchers.containsString("120"));
        new MiniJVM(classPath, "com.github.hcsp.RecursiveClass").start();
    }
}
