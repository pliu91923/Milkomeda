package com.github.yizzuide.milkomeda.demo.particle.controller;

import com.github.yizzuide.milkomeda.particle.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ParticleController
 *
 * @author yizzuide
 * Create at 2019/05/30 14:56
 */
@Slf4j
@RestController
@RequestMapping("particle")
public class ParticleController {

    @Autowired
    private IdempotentLimiter idempotentLimiter;

    @Autowired
    private TimesLimiter timesLimiter;

    @RequestMapping("check")
    public ResponseEntity check(String token) throws Throwable {
        // 使用内置的方法限制一次请求的重复调用
        return idempotentLimiter.limit(token, 60L, (particle) -> {
            // 判断是否被限制
            if (particle.isLimited()) {
                return ResponseEntity.status(406).body("请求勿重复请求");
            }
            // 测试业务处理耗时
            Thread.sleep(5000);
            return ResponseEntity.ok("ok");
        });
    }

    @RequestMapping("check2")
    // 注解的方式限制一次请求的重复调用
    @Limit(name = "user:check", key = "#token", expire = 60L)
    public ResponseEntity check2(String token, Particle particle/*这个状态值自动注入*/) throws Throwable {
        log.info("check2: token={}", token);
        // 判断是否被限制
        if (particle.isLimited()) {
            return ResponseEntity.status(406).body("请求勿重复请求");
        }
        // 模拟业务处理耗时
        Thread.sleep(5000);
        return ResponseEntity.ok("ok");
    }

    @RequestMapping("check3")
    // 注解的方式限制一次请求的重复调用
    @Limit(name = "user:check", key = "[X-Token]", expire = 60L)
    public ResponseEntity check3(Particle particle/*这个状态值自动注入*/) throws Throwable {
        // 判断是否被限制
        if (particle.isLimited()) {
            return ResponseEntity.status(406).body("请求勿重复请求");
        }
        // 模拟业务处理耗时
        Thread.sleep(5000);
        return ResponseEntity.ok("ok");
    }


    @RequestMapping("send")
    public ResponseEntity send(String phone) throws Throwable {
        // 使用内置的方法限制调用次数
        return timesLimiter.limit(phone, (particle) -> {
            if (particle.isLimited()) {
                return ResponseEntity.status(406).body("超过使用次数：" + particle.getValue() + "次");
            }
            return ResponseEntity.ok("发送成功，当前次数：" + particle.getValue());
        });
    }

    @RequestMapping("send2")
    // 注解的方式限制调用次数
    @Limit(name = "user:send", key = "#phone", limiterBeanClass = TimesLimiter.class)
    public ResponseEntity send2(String phone, Particle particle/*这个状态值自动注入*/) {
        log.info("check2: phone={}", phone);
        if (particle.isLimited()) {
            return ResponseEntity.status(406).body("超过使用次数：" + particle.getValue() + "次");
        }
        return ResponseEntity.ok("发送成功，当前次数：" + particle.getValue());
    }

    // 方法嵌套调用的组合方式
    @RequestMapping("verify")
    public ResponseEntity verify(String phone) throws Throwable {
        // 先请求重复检查
        return idempotentLimiter.limit(phone, 60L, (particle) -> {
            // 判断是否被限制
            if (particle.isLimited()) {
                return ResponseEntity.status(406).body("请求勿重复请求");
            }

            // 再检测次数
            return timesLimiter.limit(phone, (p) -> {
                if (p.isLimited()) {
                    return ResponseEntity.status(406).body("超过使用次数：" + p.getValue() + "次");
                }
                // 模拟业务处理耗时
                Thread.sleep(5000);

                return ResponseEntity.ok("发送成功，当前次数：" + p.getValue());
            });

        });
    }

    // 基于注解的拦截链组合方式
    @RequestMapping("verify2")
    @Limit(name = "user:send", key = "#phone", expire = 60L, limiterBeanClass = BarrierLimiter.class)
    public ResponseEntity verify2(String phone, Particle particle/*这个状态值自动注入*/) throws Throwable {
        log.info("verify2: phone={}", phone);
        // 判断是否被限制
        if (particle.isLimited()) {
            if (particle.getType() == IdempotentLimiter.class) {
                return ResponseEntity.status(406).body("请求勿重复请求");
            } else if (particle.getType() == TimesLimiter.class) {
                return ResponseEntity.status(406).body("超过使用次数：" + particle.getValue() + "次");
            }
        }

        // 模拟业务处理耗时
        Thread.sleep(5000);

        return ResponseEntity.ok("发送成功，当前次数：" + particle.getValue());
    }
}