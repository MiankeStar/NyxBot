package com.nyx.bot.runner;

import com.nyx.bot.utils.IoUtils;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class Runners {

    //程序启动完成启动浏览器
    @Bean
    public ApplicationRunner browser(){
        return args -> {
            IoUtils.index();
        };
    }

}
