package org.elasticsearch.index.synonym;

import org.elasticsearch.env.Environment;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.elasticsearch.index.synonym.FileRemoteMonitor;
import org.elasticsearch.index.settings.IndexSettings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.common.inject.assistedinject.Assisted;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.inject.assistedinject.Assisted;
import org.elasticsearch.index.analysis.AbstractTokenFilterFactory;
import org.elasticsearch.index.Index;
import org.apache.lucene.analysis.TokenStream;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;

import static org.elasticsearch.common.unit.TimeValue.timeValueSeconds;

public class FileRemoteSynonymTokenFilterFactory extends AbstractTokenFilterFactory {

    private Environment environment;
    public static ESLogger logger=Loggers.getLogger("synonym-remote");

    private static ScheduledExecutorService pool = Executors.newScheduledThreadPool(1);

    @Inject
    public FileRemoteSynonymTokenFilterFactory(Index index, @IndexSettings Settings indexSettings, Environment env, @Assisted String name, @Assisted Settings settings) {
        super(index, indexSettings, name, settings);
        this.environment = env;
        String path = settings.get("remote_synonyms_path");


        if (path != null) {
            // 注册Monitor
            logger.info("load remote_synonyms_path {} ", path);
            pool.scheduleAtFixedRate(new FileRemoteMonitor(path, env), 10, 60, TimeUnit.SECONDS);
        }


    }

    @Override
    public TokenStream create(TokenStream tokenStream) {
        return tokenStream;
    }
}
