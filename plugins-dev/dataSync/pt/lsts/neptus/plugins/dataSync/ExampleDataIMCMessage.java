package pt.lsts.neptus.plugins.dataSync;

import pt.lsts.imc.*;

public class ExampleDataIMCMessage extends IMCMessage {


    public static final int ID_STATIC = 150;

    public ExampleDataIMCMessage() {
        super(150);
    }

    public ExampleDataIMCMessage(IMCMessage var1) {
        super(150);

        try {
            this.copyFrom(var1);
        } catch (Exception var3) {
            var3.printStackTrace();
        }

    }

    public ExampleDataIMCMessage(IMCDefinition var1) {
        super(var1, 150);
    }

    public static ExampleDataIMCMessage create(Object... var0) {
        ExampleDataIMCMessage var1 = new ExampleDataIMCMessage();

        for(int var2 = 0; var2 < var0.length - 1; var2 += 2) {
            var1.setValue(var0[var2].toString(), var0[var2 + 1]);
        }

        return var1;
    }

    public static ExampleDataIMCMessage clone(IMCMessage var0) throws Exception {
        ExampleDataIMCMessage var1 = new ExampleDataIMCMessage();
        if (var0 == null) {
            return var1;
        } else {
            if (var0.definitions != var1.definitions) {
                var0 = var0.cloneMessage();
                IMCUtil.updateMessage(var0, var1.definitions);
            } else if (var0.getMgid() != var1.getMgid()) {
                throw new Exception("Argument " + var0.getAbbrev() + " is incompatible with message " + var1.getAbbrev());
            }

            var1.getHeader().values.putAll(var0.getHeader().values);
            var1.values.putAll(var0.values);
            return var1;
        }
    }

}
