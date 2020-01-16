package com.github.zxh.classpy.classfile;

import com.github.zxh.classpy.classfile.attribute.AttributeInfo;
import com.github.zxh.classpy.classfile.attribute.CodeAttribute;
import com.github.zxh.classpy.classfile.bytecode.Instruction;
import com.github.zxh.classpy.classfile.constant.ConstantPool;
import com.github.zxh.classpy.classfile.datatype.Table;
import com.github.zxh.classpy.classfile.datatype.U2;
import com.github.zxh.classpy.classfile.jvm.AccessFlagType;

import java.util.List;

/*
method_info {
    u2             access_flags;
    u2             name_index;
    u2             descriptor_index;
    u2             attributes_count;
    attribute_info attributes[attributes_count];
}
 */
public class MethodInfo extends ClassFilePart {

    {
        u2af("access_flags", AccessFlagType.AF_METHOD);
        u2cp("name_index");
        u2cp("descriptor_index");
        u2("attributes_count");
        table("attributes", AttributeInfo.class);
    }

    @Override
    protected void postRead(ConstantPool cp) {
        int nameIndex = super.getUInt("name_index");
        if (nameIndex > 0) {
            // todo fix loading java.lang.String from rt.jar
            setDesc(cp.getUtf8String(nameIndex));
        }
    }

    public int getMaxStack() {
        U2 maxStackU2 = (U2) getCodeAttribute()
                .getParts()
                .stream()
                .filter(part -> part instanceof U2 && part.getName().equals("max_stack"))
                .findFirst()
                .orElseThrow(IllegalStateException::new);
        return maxStackU2.getValue();
    }

    public int getMaxLocals() {
        U2 maxLocals = (U2) getCodeAttribute()
                .getParts()
                .stream()
                .filter(part -> part instanceof U2 && part.getName().equals("max_locals"))
                .findFirst()
                .orElseThrow(IllegalStateException::new);
        return maxLocals.getValue();
    }

    public Table getAttributes() {
        return (Table) getParts().stream().filter(part -> part instanceof Table && part.getName().equals("attributes")).findFirst().orElseThrow(IllegalArgumentException::new);
    }

    public CodeAttribute getCodeAttribute() {
        return (CodeAttribute) getAttributes().getParts().stream().filter(part -> part instanceof CodeAttribute).findFirst().orElseThrow(IllegalArgumentException::new);
    }

    public List<Instruction> getCode() {
        CodeAttribute.Code code = (CodeAttribute.Code) getCodeAttribute()
                .getParts()
                .stream()
                .filter(part -> part instanceof CodeAttribute.Code && part.getName().equals("code"))
                .findFirst()
                .orElseThrow(IllegalStateException::new);
        return (List) code.getParts();
    }
}
