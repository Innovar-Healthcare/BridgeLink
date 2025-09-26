package com.mirth.connect.plugins.dynamiclookup.server.cache;

public class NoCacheWrapper<K, V> implements SimpleCache<K, V> {
	@Override
	public V get(K key) {
		return null;
	}

	@Override
	public void put(K key, V value) {
		/* no-op */ }

	@Override
	public void remove(K key) {
		/* no-op */ }

	@Override
	public void clear() {
		/* no-op */ }

	@Override
	public int size() {
		return 0;
	}
}
