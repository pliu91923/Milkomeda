package com.github.yizzuide.milkomeda.mix.collector;

import com.github.yizzuide.milkomeda.comet.CometData;
import com.github.yizzuide.milkomeda.pillar.Pillar;

/**
 * Collector
 * 日志收集器
 *
 * @author yizzuide
 * @since 1.15.0
 * Create at 2019/11/13 18:12
 */
public interface Collector extends Pillar<CometData, Object> {

    /**
     * 数据准备
     * @param params CometData
     */
    void prepare(CometData params);

    /**
     * 执行成功
     * @param params CometData
     */
    void onSuccess(CometData params);

    /**
     * 执行失败
     * @param params CometData
     */
    void onFailure(CometData params);
}
