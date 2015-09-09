package org.elasticsearch.index.synonym;

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

import static org.elasticsearch.common.unit.TimeValue.timeValueSeconds;

public class FileRemoteMonitor implements Runnable {

    private static CloseableHttpClient httpclient = HttpClients.createDefault();
    public static ESLogger logger=Loggers.getLogger("synonym-remote");

    private String remoteAddr;
    private String lastModified;

    private SynonymMap synonymMap;

    private Analyzer analyzer;

    public FileRemoteMonitor(String remoteAddr)
    {
        this.remoteAddr = remoteAddr;
        this.lastModified = null;
    }

    @Override
    public void run()
    {
        //超时设置
		RequestConfig rc = RequestConfig.custom().setConnectionRequestTimeout(10*1000)
				.setConnectTimeout(10*1000).setSocketTimeout(15*1000).build();

		HttpHead head = new HttpHead(remoteAddr);
		head.setConfig(rc);

		//设置请求头
		if (lastModified != null) {
			head.setHeader("If-Modified-Since", lastModified);
		}

        this.analyzer = new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName, Reader reader) {
                Tokenizer tokenizer = new WhitespaceTokenizer(Lucene.ANALYZER_VERSION, reader);
                TokenStream stream = new LowerCaseFilter(Lucene.ANALYZER_VERSION, tokenizer);
                return new TokenStreamComponents(tokenizer, stream);
            }
        };

		CloseableHttpResponse response = null;
		try {

			response = httpclient.execute(head);

			//返回200 才做操作
			if(response.getStatusLine().getStatusCode()==200){

				if (!response.getLastHeader("Last-Modified").getValue().equalsIgnoreCase(lastModified)) {

                    Reader rulesReader = new InputStreamReader(new URL(remoteAddr).openStream(), Charsets.UTF_8);

                    SynonymMap.Builder parser = null;

                    parser = new SolrSynonymParser(true, true, analyzer);
                    ((SolrSynonymParser) parser).parse(rulesReader);

                    synonymMap = parser.build();
                    lastModified = response.getLastHeader("Last-Modified").getValue();
				}
			}else if (response.getStatusLine().getStatusCode()==304) {
				//没有修改，不做操作
				//noop
			}else{
				logger.info("remote_synonyms_path {} return bad code {}" , remoteAddr , response.getStatusLine().getStatusCode() );
			}

		} catch (Exception e) {
			logger.info("remote_synonyms_path {} error!",e , remoteAddr);
		}finally{
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
