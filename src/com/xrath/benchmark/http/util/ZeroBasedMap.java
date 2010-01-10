package com.xrath.benchmark.http.util;

import java.util.HashMap;
import java.util.Map;

public class ZeroBasedMap<K, V> extends HashMap<K, V> {
	private static final long serialVersionUID = -5401164708340576532L;
	
	private static Long zero = 0L;

	public ZeroBasedMap() {
		
	}
	
	@SuppressWarnings("unchecked")
	public void add( K key, Long value ) {
		Long v = (Long)get(key);
		put(key, (V)new Long(v+value));
	}
	
	public void addAll( Map<K, V> map ) {
		for(K key : map.keySet()) {
			add(key, (Long)map.get(key));
		}
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public V get( Object key ) {
		if( !containsKey(key) ) {
			return (V)zero;
		}
		return super.get(key);
	}
}
