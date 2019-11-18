package com.github.yizzuide.milkomeda.ice;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.yizzuide.milkomeda.util.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * RedisIce
 *
 * @author yizzuide
 * @since 1.15.0
 * @version 1.15.1
 * Create at 2019/11/16 15:20
 */
public class RedisIce implements Ice {

    @Autowired
    private JobPool jobPool;

    @Autowired
    private DelayBucket delayBucket;

    @Autowired
    private ReadyQueue readyQueue;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private IceProperties props;

    @Override
    public void add(Job job) {
        job.setId(job.getTopic() + "-" + job.getId());
        job.setStatus(JobStatus.DELAY);
        RedisUtil.batchOps(() -> {
            jobPool.push(job);
            delayBucket.add(new DelayJob(job));
        }, redisTemplate);
    }

    @Override
    public <T> void add(String id, String topic, T body, long delay) {
        Job<T> job = new Job<>(id, topic, delay, props.getTtr(), props.getRetryCount(), body);
        add(job);
    }

    @Override
    public <T> Job<T> pop(String topic) {
        DelayJob delayJob = readyQueue.pop(topic);
        if (delayJob == null) {
            return null;
        }
        Job<T> job = jobPool.getByType(delayJob.getJodId(), new TypeReference<Job<T>>(){});
        // 元数据已经删除，则取下一个
        if (job == null) {
            job = pop(topic);
            return job;
        }

        Job<T> mJob = job;
        RedisUtil.batchOps(() -> {
            // 设置为处理中状态
            mJob.setStatus(JobStatus.RESERVED);
            // 更新延迟时间为TTR
            delayJob.setDelayTime(System.currentTimeMillis() + mJob.getTtr());
            jobPool.push(mJob);
            delayBucket.add(delayJob);
        }, redisTemplate);
        return mJob;
    }

    @Override
    public <T> List<Job<T>> pop(String topic, int count) {
        List<DelayJob> delayJobList = count == 1 ?
                Collections.singletonList(readyQueue.pop(topic)) : readyQueue.pop(topic, count);
        if (CollectionUtils.isEmpty(delayJobList)) {
            return null;
        }
        List<String> jobIds = delayJobList.stream().map(DelayJob::getJodId).collect(Collectors.toList());
        List<Job<T>> jobList = jobPool.getByType(jobIds, new TypeReference<Job<T>>(){}, count);
        // 元数据已经删除，则取下一个
        if (CollectionUtils.isEmpty(jobList)) {
            jobList = pop(topic, count);
            return jobList;
        }
        List<Job<T>> mJobList = jobList;
        RedisUtil.batchOps(() -> {
            for (int i = 0; i < mJobList.size(); i++) {
                Job mJob = mJobList.get(i);
                // 设置为处理中状态
                mJob.setStatus(JobStatus.RESERVED);
                // 更新延迟时间为TTR
                DelayJob delayJob = delayJobList.get(i);
                delayJob.setDelayTime(System.currentTimeMillis() + mJob.getTtr());
            }
            jobPool.push(mJobList);
            delayBucket.add(delayJobList);
        }, redisTemplate);
        return jobList;
    }

    @Override
    public <T> void finish(List<Job<T>> jobs) {
        delete(jobs);
    }

    @Override
    public void finish(Object... jobIds) {
        delete(jobIds);
    }

    @Override
    public <T> void delete(List<Job<T>> jobs) {
        List<String> jobIds = jobs.stream().map(Job::getId).collect(Collectors.toList());
        delete(jobIds.toArray(new Object[]{}));
    }

    @Override
    public void delete(Object... jobIds) {
        jobPool.remove(jobIds);
    }
}
