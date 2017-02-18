package com.winterwell.gson;

import java.lang.reflect.Type;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.winterwell.utils.time.Time;

/**
 * TODO move some of our adapters in here for our convenience
 * @author daniel
 *
 */
public class StandardAdapters {


/**
 * Time <-> iso-string
 * Warning: This loses the type info! 
 * It looks cleaner, but the inverse only works if the field is of type Time (and it is is slightly slower). 
 * Use-case: good for Elastic-Search
 * @author daniel
 */
public static class TimeTypeAdapter implements JsonSerializer<Time>, JsonDeserializer<Time> {
	@Override
	public JsonElement serialize(Time src, Type srcType,
			JsonSerializationContext context) {
		return new JsonPrimitive(src.toISOString());
	}

	@Override
	public Time deserialize(JsonElement json, Type type,
			JsonDeserializationContext context) throws JsonParseException {
		return new Time(json.getAsString());
	}
}

/**
 * @deprecated Not sure why we have this!
 * @author daniel
 */
public static class ClassTypeAdapter implements JsonSerializer<Class>,
		JsonDeserializer<Class> {
	@Override
	public JsonElement serialize(Class src, Type srcType,
			JsonSerializationContext context) {
		return new JsonPrimitive(src.getCanonicalName());
	}

	@Override
	public Class deserialize(JsonElement json, Type type,
			JsonDeserializationContext context) throws JsonParseException {
		try {
			return Class.forName(json.getAsString());
		} catch (ClassNotFoundException e) {
			throw new JsonParseException(e);
		}
	}
}



}
