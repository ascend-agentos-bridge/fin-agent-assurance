package com.assurance.config;
public final class ModelSnapshot {
    private final ModelConfigView view; private final String secret;
    public ModelSnapshot(ModelConfigView view,String secret){this.view=view;this.secret=secret;}
    public ModelConfigView view(){return view;} public String secret(){return secret;}
    @Override public String toString(){return "ModelSnapshot[version="+view.version()+", credentials redacted]";}
}
