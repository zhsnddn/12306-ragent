package com.ming.agent12306.plan.aop;

import com.ming.agent12306.plan.model.PlanStepType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PlanStepLog {
    PlanStepType value();
}
