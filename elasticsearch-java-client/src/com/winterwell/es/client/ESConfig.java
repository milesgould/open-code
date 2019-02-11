package com.winterwell.es.client;

import com.winterwell.gson.Gson;
import com.winterwell.utils.Dep;
import com.winterwell.utils.Utils;
import com.winterwell.utils.io.Option;
import com.winterwell.utils.time.TUnit;
import com.winterwell.utils.time.Time;

public class ESConfig {

	public static final String CLIENT_VERSION = "0.9";
	public static final String ES_SUPPORTED_VERSION = "5.0";
	
	/**
	 * This is for code that wishes to dynamically change based on the ES version its connecting to.
	 * It is NOT auto-set!
	 * e.g. 2.4 for older ES or 6.2 for the latest (at time of writing).
	 */
	@Option
	public double esVersion;
	
	@Override
	public String toString() {
		return "ESConfig[esUrl=" + esUrl + "]";
	}

	public ESConfig() {
	}
	
	/**
	 * @deprecated set esUrl instead
	 * 
	 * The normal default for ES is 9200. This can be changed in /etc/elasticsearch/elasticsearch.yml
	 * -1 to not list a port in the url (ie use the port 80/443 http/https default).
	 */
	@Option
	private int port = 9200;
	
	/**
	 * @deprecated set esUrl instead
	 */
	@Option
	private String server = "localhost";
	
	/**
	 * @deprecated set esUrl instead
	 */
	@Option
	private String protocol = "http";
		
	@Option
	public String esUrl = "http://localhost:9200";

	public String getESUrl() {
		if ( ! Utils.isBlank(esUrl)) return esUrl;
		return protocol+"://"+server+(port>0? ":"+port : "");
	}
	
	private Gson gson;
	
	/**
	 * Bit of a hack. When creating indices, its nice to use a versioned-name + public-alias.
	 * This is a convenient place for saying what version to use (and which can be altered by a config file). 
	 */
	@Option
	private String indexAliasVersion = new Time().format("MMMyy").toLowerCase();
	
	@Option(description="milliseconds for the http request to timeout")
	public long esRequestTimeout = TUnit.MINUTE.millisecs;
		
	public Gson getGson() {
		if (gson!=null) return gson;
		// if Gson has not been setup yet, return a vanilla one for now (but don't set it)
		gson = Dep.has(Gson.class)? Dep.get(Gson.class) : null;
		return gson==null? new Gson() : gson;
	}
	
	public ESConfig setGson(Gson gson) {
		this.gson = gson;		
		// ??could we put in a type adapter
		return this;
	}

	/**
	 * Convenience hack: It's handy to make an index with a version name (e.g. "foo_jun18")
	 * and an alias with a public name (e.g. "foo"). This provides a convenient place
	 * to set which "version" to use for new indices.
	 * It is not directly used in the ES client itself, though it can be loaded from the
	 * .properties file.
	 */
	public String getIndexAliasVersion() {
		return indexAliasVersion;
	}
	public void setIndexAliasVersion(String indexAliasVersion) {
		this.indexAliasVersion = indexAliasVersion;
	}
	
}
