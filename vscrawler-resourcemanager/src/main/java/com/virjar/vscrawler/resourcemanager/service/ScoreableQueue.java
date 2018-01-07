package com.virjar.vscrawler.resourcemanager.service;

/**
 * Created by virjar on 2018/1/4.<br/>
 *
 * @author virjar
 * @since 0.2.2
 * 支持评分的队列
 */
public interface ScoreableQueue<E> {
    long size(String queueID);

    boolean addFirst(String queueID, E e);

    boolean addLast(String queueID, E e);

    boolean addIndex(String queueID, long index, E e);

    E poll(String queueID);

    E take(String queueID);

    E take(String queueID, long timeOut);
}
