package org.elasticsearch.plugin.synonym;

import org.elasticsearch.common.collect.Lists;
import org.elasticsearch.common.inject.Module;
import org.elasticsearch.plugins.AbstractPlugin;
import org.elasticsearch.index.analysis.AnalysisModule;
import org.apache.lucene.analysis.Tokenizer;

import java.util.Collection;
import org.elasticsearch.index.synonym.FileRemoteSynonymTokenFilterFactory;

public class RemotePlugin extends AbstractPlugin {
    @Override public String name() {
        return "synonym-remote";
    }

    @Override public String description() {
        return "synonym remote load doc";
    }

    @Override public void processModule(Module module) {
        if (module instanceof AnalysisModule) {
            AnalysisModule analysisModule = (AnalysisModule) module;
            ((AnalysisModule) module).addTokenFilter("file_remote_synonym", FileRemoteSynonymTokenFilterFactory.class);
        }
    }
}
