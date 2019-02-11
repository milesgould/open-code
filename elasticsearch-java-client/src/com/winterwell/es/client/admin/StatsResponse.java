package com.winterwell.es.client.admin;

import java.util.Map;

import com.winterwell.es.client.ESHttpResponse;

public class StatsResponse extends ESHttpResponse {

	public StatsResponse(ESHttpResponse response) {
		super(response);
	}

	public Map<String,Map> getIndices() {
		return (Map) getParsedJson().get("indices");
	}
}
