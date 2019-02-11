package com.winterwell.es.client;

import java.util.Arrays;
import java.util.List;

import com.winterwell.utils.StrUtils;

public class ClearScrollRequestBuilder extends ESHttpRequest<ClearScrollRequestBuilder, IESResponse> {

	public ClearScrollRequestBuilder(ESHttpClient esHttpClient) {
		super(esHttpClient, "_search/scroll");
		method = "DELETE";
	}

	public ClearScrollRequestBuilder setScrollIds(List<String> asList) {
		params.put("scroll_id", StrUtils.join(asList, ","));
		return this;
	}

	/**
	 * @deprecated Use {@link #setScrollIds(List)}
	 */
	@Override
	public ClearScrollRequestBuilder setId(String id) {
		throw new IllegalStateException("You want setScrollIds");
	}

	public ClearScrollRequestBuilder setScrollId(String scrollId) {
		return setScrollIds(Arrays.asList(scrollId));
	}
}
