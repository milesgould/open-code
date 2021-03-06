package com.winterwell.depot.merge;

import java.lang.reflect.Array;
import java.util.List;
import java.util.Map;

import com.winterwell.depot.Desc;
import com.winterwell.utils.log.Log;

/**
 * Collect together all the standard mergers.
 * @author daniel
 *
 */
public class Merger implements IMerger<Object> {

	/**
	 * Convenience for use in unit-tests. assert two objects are the same
	 *  -- and use the diff as a helpful error message if they are not.
	 * @param a
	 * @param b
	 */
	public void assertSame(Object a, Object b) {
		Diff diff = diff(a, b);
		if (diff==null) return;
		assert false : diff;
	}
	
	final ClassMap<IMerger> mergers = new ClassMap<>();
	
	public IMerger getMerger(Class klass) {
		IMerger m = mergers.get(klass);
		return m;		
	}
	
	private static final String TAG = "Merger";

	public Merger() {
		initStdMergers();
	}

	
	/**
	 * Number, Map and List
	 */
	protected void initStdMergers() {
		if (mergers.get(Number.class)==null) {
			addMerge(Number.class, new NumMerger());
		}
		if (mergers.get(Map.class)==null) {
			addMerge(Map.class, new MapMerger(this));
		}
		if (mergers.get(List.class)==null) {
			addMerge(List.class, new ListMerger(this));
		}
		// must be after ListMerger
		if (mergers.get(Array.class)==null) {
			addMerge(Array.class, new ArrayMerger(this));
		}
		if (false && mergers.get(String.class)==null) {
			addMerge(String.class, new StringMerger());
		}
		// TODO POJOMerger can cause problems by intercepting eg Boolean
		if (false && mergers.get(Object.class)==null) {
			addMerge(Object.class, new POJOMerger(this));
		}
	}

	public void addMerge(Class handles, IMerger merger) {
		mergers.put(handles, merger);
		mergers.put(merger.getClass(), merger);
	}

	public ClassMap<IMerger> getMergers() {
		return mergers;
	}

	/**
	 * 
	 * @param before
	 * @param after
	 * @param latest
	 * @return
	 */
	public Object doMerge(Object before, Object after, Object latest) {
		if (latest==null) return after;
		Class type = after.getClass();
		IMerger m = mergers.get(type);
		if (m==null) {
			Log.e(TAG, "No merger for "+type);
			return after;
		}
		return m.doMerge(before, after, latest);
	}

	@Override
	public Diff diff(Object before, Object after) {
		Class type = after.getClass();
		IMerger m = mergers.get(type);
		if (m==null) {
			throw new IllegalStateException("No merger for "+type);
		}
		return m.diff(before, after);
	}

	@Override
	public Object applyDiff(Object a, Diff diff) {
		if (diff==null) return a;
		Class type = a.getClass();
		IMerger m = mergers.get(type);
		if (m==null) {
			throw new IllegalStateException("No merger for "+type);
		}
		return m.applyDiff(a, diff);
	}

	@Override
	public Object stripDiffs(Object v) {
		if (v instanceof Diff) {
			return stripDiffs(((Diff) v).diff);
		}
		// ??Is this right??
		Class type = v.getClass();
		IMerger m = mergers.get(type);
		if (m==null) {
			return v;
		}
		return m.stripDiffs(v);
	}


	@Override
	public boolean useMerge(Desc<? extends Object> desc) {
		return true;
	}

}
