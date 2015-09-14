package org.elasticsearch.index.synonym;

import org.elasticsearch.env.Environment;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
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

import org.elasticsearch.env.Environment;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.apache.lucene.analysis.synonym.SolrSynonymParser;
import org.apache.lucene.analysis.synonym.SynonymFilter;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.apache.lucene.analysis.synonym.WordnetSynonymParser;
import org.elasticsearch.common.lucene.Lucene;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpHead;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import java.io.Reader;
import java.io.InputStreamReader;
import java.net.URL;
import org.elasticsearch.common.base.Charsets;
import java.io.File;

import org.elasticsearch.index.analysis.TokenizerFactory;
import org.elasticsearch.index.analysis.TokenizerFactoryFactory;
import org.elasticsearch.indices.IndicesService;
import org.elasticsearch.indices.analysis.IndicesAnalysisService;
import java.util.Map;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.ElasticsearchIllegalArgumentException;
import java.util.concurrent.ScheduledFuture;
import org.elasticsearch.indices.IndicesLifecycle;
import org.elasticsearch.index.settings.IndexSettings;

import static org.elasticsearch.common.unit.TimeValue.timeValueSeconds;

public class FileRemoteSynonymTokenFilterFactory extends AbstractTokenFilterFactory {

    private Environment environment;
    private SynonymMap synonymMap;
    private String remoteAddr;
    private String lastModified;
    private Analyzer analyzer;
    private Integer reloadInterval = 0;

    private volatile ScheduledFuture scheduledFuture;

    public static ESLogger logger = Loggers.getLogger("synonym-remote");
    private static CloseableHttpClient httpclient = HttpClients.createDefault();

    @Inject
    public FileRemoteSynonymTokenFilterFactory(Index index, @IndexSettings Settings indexSettings, Environment env, IndicesAnalysisService indicesAnalysisService, Map<String, TokenizerFactoryFactory> tokenizerFactories,
                                                @Assisted String name, @Assisted Settings settings, ThreadPool threadPool, IndicesService indicesService) {
        super(index, indexSettings, name, settings);
        this.environment = env;
        this.remoteAddr = settings.get("remote_synonyms_path");
        this.reloadInterval = settings.getAsInt("reload_interval", 10);

        if (remoteAddr != null) {

            this.analyzer = new Analyzer() {
                @Override
                protected TokenStreamComponents createComponents(String fieldName, Reader reader) {
                    Tokenizer tokenizer = new WhitespaceTokenizer(Lucene.ANALYZER_VERSION, reader);
                    TokenStream stream =new LowerCaseFilter(Lucene.ANALYZER_VERSION, tokenizer);

                    return new TokenStreamComponents(tokenizer, stream);
                }
            };

            // set monitor
            logger.info("load remote_synonyms_path {} ", remoteAddr);

            try{
                Reader rulesReader = new InputStreamReader(new URL(remoteAddr).openStream(), Charsets.UTF_8);
                SynonymMap.Builder parser = null;

                parser = new SolrSynonymParser(true, true, analyzer);
                ((SolrSynonymParser) parser).parse(rulesReader);
                synonymMap = parser.build();
            } catch (Exception e) {
                e.printStackTrace();
            }

            scheduledFuture = threadPool.scheduleWithFixedDelay(new FileRemoteMonitor(), timeValueSeconds(this.reloadInterval));
        }

    }

    @Override
    public TokenStream create(TokenStream tokenStream) {
        return synonymMap.fst == null ? tokenStream : new SynonymFilter(tokenStream, synonymMap, true);
    }

    public class FileRemoteMonitor implements Runnable {

        @Override
        public void run()
        {
            //exceed time
            RequestConfig rc = RequestConfig.custom().setConnectionRequestTimeout(10*1000)
                    .setConnectTimeout(10*1000).setSocketTimeout(15*1000).build();

            HttpHead head = new HttpHead(remoteAddr);
            head.setConfig(rc);

            //request modified
            if (lastModified != null) {
                head.setHeader("If-Modified-Since", lastModified);
            }

            CloseableHttpResponse response = null;
            try {
                response = httpclient.execute(head);

                //return 200 it will reload
                if(response.getStatusLine().getStatusCode() == 200){
                    if (!response.getLastHeader("Last-Modified").getValue().equalsIgnoreCase(lastModified)) {
                        Reader rulesReader = new InputStreamReader(new URL(remoteAddr).openStream(), Charsets.UTF_8);

                        SynonymMap.Builder parser = null;
                        synonymMap = null;

                        parser = new SolrSynonymParser(true, true, analyzer);
                        ((SolrSynonymParser) parser).parse(rulesReader);
                        synonymMap = parser.build();

                        lastModified = response.getLastHeader("Last-Modified").getValue();
                        logger.info("remote_synonyms_path {} load success", remoteAddr);
                    }
                } else if (response.getStatusLine().getStatusCode() == 304) {
                    // do nothing
                } else {
                    logger.info("remote_synonyms_path {} return bad code {}" , remoteAddr , response.getStatusLine().getStatusCode() );
                }

            } catch (Exception e) {
                logger.info("remote_synonyms_path {} error!",e , remoteAddr);
            } finally {
                try {
                    if (response != null) {
                        response.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

}
