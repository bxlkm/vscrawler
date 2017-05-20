package com.virjar.vscrawler.serialize;

import java.util.Collection;

import com.virjar.vscrawler.seed.Seed;

/**
 * Created by virjar on 17/4/16.
 * 
 * @author virjar
 * @since 0.0.1
 */
public interface Pipeline {
    void saveItem(Collection<String> itemJson, Seed seed);
}