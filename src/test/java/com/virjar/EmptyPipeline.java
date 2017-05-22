package com.virjar;

import java.util.Collection;

import com.virjar.vscrawler.seed.Seed;
import com.virjar.vscrawler.serialize.Pipeline;

/**
 * Created by virjar on 17/5/22.
 */
public class EmptyPipeline implements Pipeline {
    @Override
    public void saveItem(Collection<String> itemJson, Seed seed) {
        System.out.println(seed.getData() + " 处理完成");
    }
}
