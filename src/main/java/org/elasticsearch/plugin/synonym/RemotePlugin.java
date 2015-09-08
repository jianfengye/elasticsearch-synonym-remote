package org.elasticsearch.plugin.example;
import org.elasticsearch.common.collect.Lists;
import org.elasticsearch.common.inject.Module;
import org.elasticsearch.plugins.AbstractPlugin;

import java.util.Collection;

public class RemotePlugin extends AbstractPlugin {
    @Override public String name() {
        return "synonym-remote";
    }

    @Override public String description() {
        return "synonym remote load doc";
    }

    @Override
    public Collection<Class<? extends Module>> modules() {
        Collection<Class<? extends Module>> modules = Lists.newArrayList();
        modules.add(ExampleRestModule.class);
        return modules;
    }
}
