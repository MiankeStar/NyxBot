package com.nyx.bot.data;


import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONReader;
import com.nyx.bot.core.ApiUrl;
import com.nyx.bot.entity.warframe.*;
import com.nyx.bot.enums.HttpCodeEnum;
import com.nyx.bot.repo.warframe.*;
import com.nyx.bot.res.GlobalStates;
import com.nyx.bot.utils.CacheUtils;
import com.nyx.bot.utils.FileUtils;
import com.nyx.bot.utils.SpringUtils;
import com.nyx.bot.utils.gitutils.JgitUtil;
import com.nyx.bot.utils.http.HttpUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.MalformedURLException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Component
public class WarframeDataSource {

    static final String DATA_SOURCE_PATH = JgitUtil.lockPath + "/warframe/";

    public static void init() {
        log.info("开始初始化数据！");
        // allOf等待所有任务完成
        CompletableFuture.allOf(
                // supplyAsync 异步获取数据,返回Boolean值用于下一个线程
                CompletableFuture.supplyAsync(WarframeDataSource::cloneDataSource)
                        // 根据上一个线程的返回值进行操作
                        .thenAccept(flag -> {
                            if (flag) {
                                log.error("初始化数据模板，失败！请检查网络环境之后重启程序！");
                                System.exit(SpringApplication.exit(SpringUtils.getApplicationContext(), () -> -1));
                            } else {
                                CompletableFuture.allOf(CompletableFuture.runAsync(WarframeDataSource::getAlias)
                                        .thenRunAsync(WarframeDataSource::getRivenTion)
                                        .thenRunAsync(WarframeDataSource::getRivenTionAlias)
                                        .thenRunAsync(WarframeDataSource::initTranslation)
                                        .thenRunAsync(WarframeDataSource::getRivenAnalyseTrend)
                                        .thenRunAsync(WarframeDataSource::getRivenTrend)).join();
                            }
                        }),
                // 初始化网络数据
                CompletableFuture.runAsync(WarframeDataSource::initWarframeStatus)
                        .thenRunAsync(WarframeDataSource::getEphemeras)
                        .thenRunAsync(WarframeDataSource::getMarket)
                        .thenRunAsync(WarframeDataSource::getWeapons)
                        .thenRunAsync(WarframeDataSource::getRivenWeapons)
                        .thenRunAsync(WarframeDataSource::getRelics)
        ).thenRun(() -> log.info("数据初始化完成！"));
    }

    public static void initWarframeStatus() {
        String str = FileUtils.readFileToString("./data/status");
        if (!str.isEmpty()) {
            GlobalStates globalStates = JSON.parseObject(str, GlobalStates.class);
            CacheUtils.setGlobalState(globalStates);
        }
    }

    //幻纹
    public static void getEphemeras() {
        log.info("开始获取幻纹信息！");
        HttpUtils.Body body = HttpUtils.sendGet(ApiUrl.WARFRAME_MARKET_LICH_EPHEMERAS, ApiUrl.LANGUAGE_ZH_HANS);
        if (!body.getCode().equals(HttpCodeEnum.SUCCESS)) {
            log.warn("赤毒幻纹信息初始化错误！未获取到数据信息！30秒后尝试重新获取！");
            try {
                TimeUnit.SECONDS.sleep(30);
                getEphemeras();
                return;
            } catch (InterruptedException ignored) {
                getEphemeras();
                return;
            }
        }
        String s = body.getBody();
        List<Ephemeras> ephemerasList = JSONObject.parseObject(s).getJSONObject("payload").getJSONArray("ephemeras").toJavaList(Ephemeras.class, JSONReader.Feature.SupportSmartMatch);
        body = HttpUtils.sendGet(ApiUrl.WARFRAME_MARKET_SISTER_EPHEMERAS, ApiUrl.LANGUAGE_ZH_HANS);
        if (!body.getCode().equals(HttpCodeEnum.SUCCESS)) {
            log.warn("信条幻纹信息初始化错误！未获取到数据信息！请检查网络！");
            return;
        }
        s = body.getBody();
        ephemerasList.addAll(JSONObject.parseObject(s).getJSONObject("payload").getJSONArray("ephemeras").toJavaList(Ephemeras.class, JSONReader.Feature.SupportSmartMatch));
        EphemerasRepository repository = SpringUtils.getBean(EphemerasRepository.class);
        if (!repository.findAll().isEmpty()) {
            List<Ephemeras> all = repository.findAll();
            List<Ephemeras> list = ephemerasList.stream()
                    .filter(i -> !all.stream()
                            .collect(Collectors.toMap(m -> m.getItemName() + m.getUrlName() + m.getElement(), value -> value))
                            .containsKey(i.getItemName() + i.getUrlName() + i.getElement())
                    ).toList();
            repository.saveAll(list);
            log.info("共更新Warframe.Ephemeras {} 条数据！", list.size());
        } else {
            repository.saveAll(ephemerasList);
            log.info("共更新Warframe.Ephemeras {} 条数据！", ephemerasList.size());
        }
    }

    //Market
    public static Integer getMarket() {
        log.info("开始获取Market数据！");
        HttpUtils.Body body = HttpUtils.sendGet(ApiUrl.WARFRAME_MARKET_ITEMS, ApiUrl.LANGUAGE_ZH_HANS);
        if (!body.getCode().equals(HttpCodeEnum.SUCCESS)) {
            log.warn("Market初始化错误！未获取到数据信息！30秒后尝试重新获取！");
            try {
                TimeUnit.SECONDS.sleep(30);
                getMarket();
                return -1;
            } catch (InterruptedException ignored) {
                getMarket();
                return -1;
            }
        }

        List<OrdersItems> items = JSON.parseObject(body.getBody()).getJSONObject("payload").getJSONArray("items").toJavaList(OrdersItems.class, JSONReader.Feature.SupportSmartMatch);
        OrdersItemsRepository ordersItem = SpringUtils.getBean(OrdersItemsRepository.class);
        if (!ordersItem.findAll().isEmpty()) {
            List<OrdersItems> all = ordersItem.findAll();
            List<OrdersItems> list = items.stream()
                    .filter(item -> !all.stream()
                            .collect(Collectors.toMap(m -> m.getItemName() + m.getUrlName(), value -> value))
                            .containsKey(item.getItemName() + item.getUrlName())).toList();
            log.info("共更新Warframe.Market {} 条数据！", list.size());
            return ordersItem.saveAll(list).size();
        } else {
            log.info("共初始化Warframe.Market {} 条数据！", items.size());
            return ordersItem.saveAll(items).size();

        }
    }

    //赤毒武器/信条武器
    public static Integer getWeapons() {
        log.info("开始获取武器信息！");
        HttpUtils.Body body = HttpUtils.sendGet(ApiUrl.WARFRAME_MARKET_LICH_WEAPONS, ApiUrl.LANGUAGE_ZH_HANS);
        if (!body.getCode().equals(HttpCodeEnum.SUCCESS)) {
            log.warn("赤毒武器信息初始化错误！未获取到数据信息！30秒后尝试重新获取！");
            try {
                TimeUnit.SECONDS.sleep(30);
                getWeapons();
                return -1;
            } catch (InterruptedException ignored) {
                getWeapons();
                return -1;
            }
        }
        List<Weapons> weapons = JSONObject.parseObject(body.getBody())
                .getJSONObject("payload")
                .getJSONArray("weapons")
                .toJavaList(Weapons.class, JSONReader.Feature.SupportSmartMatch);


        body = HttpUtils.sendGet(ApiUrl.WARFRAME_MARKET_SISTER_WEAPONS, ApiUrl.LANGUAGE_ZH_HANS);
        if (!body.getCode().equals(HttpCodeEnum.SUCCESS)) {
            log.warn("信条武器信息初始化错误！未获取到数据信息！请检查网络！");
            try {
                TimeUnit.SECONDS.sleep(30);
                getWeapons();
                return -1;
            } catch (InterruptedException ignored) {
                return -1;
            }
        }
        weapons.addAll(JSONObject.parseObject(body.getBody()).getJSONObject("payload").getJSONArray("weapons").toJavaList(Weapons.class, JSONReader.Feature.SupportSmartMatch));

        WeaponsRepository repository = SpringUtils.getBean(WeaponsRepository.class);
        if (!repository.findAll().isEmpty()) {
            List<Weapons> all = repository.findAll();
            List<Weapons> list = weapons.stream()
                    .filter(item -> !all.stream()
                            .collect(Collectors.toMap(m -> m.getItemName() + m.getUrlName(), value -> value))
                            .containsKey(item.getItemName() + item.getUrlName())).toList();
            log.info("共更新Warframe.Weapons {} 条数据！", list.size());
            return repository.saveAll(list).size();
        } else {
            log.info("共初始化Warframe.Weapons {} 条数据！", weapons.size());
            return repository.saveAll(weapons).size();
        }
    }

    //紫卡武器
    public static Integer getRivenWeapons() {
        log.info("开始获取Market紫卡武器数据！");
        HttpUtils.Body body = HttpUtils.sendGet(ApiUrl.WARFRAME_MARKET_RIVEN_ITEMS, ApiUrl.LANGUAGE_ZH_HANS);
        if (!body.getCode().equals(HttpCodeEnum.SUCCESS)) {
            log.warn("Market紫卡武器初始化错误！未获取到数据信息！30秒后尝试重新获取！");
            try {
                TimeUnit.SECONDS.sleep(30);
                getRivenWeapons();
                return -1;
            } catch (InterruptedException ignored) {
                getRivenWeapons();
                return -1;
            }
        }
        //获取数据源
        List<RivenItems> items = JSON.parseObject(body.getBody())
                .getJSONObject("payload")
                .getJSONArray("items")
                .toJavaList(RivenItems.class, JSONReader.Feature.SupportSmartMatch);

        RivenItemsRepository repository = SpringUtils.getBean(RivenItemsRepository.class);
        AtomicInteger size = new AtomicInteger();
        //判断数据库表中是否有数据
        if (!repository.findAll().isEmpty()) {
            List<RivenItems> all = repository.findAll();
            //stream取差集，对比与数据库中不同的数据
            List<RivenItems> list = items.stream().filter(item ->
                            !all.stream()
                                    //采用Map Key的方式对比多属性不同的值
                                    .collect(Collectors.toMap(ri -> ri.getItemName() + "-" + ri.getIconFormat() + "-" + ri.getUrlName(), value -> value))
                                    .containsKey(item.getItemName() + "-" + item.getIconFormat() + "-" + item.getUrlName())

                    )
                    //接受结果到List集合中
                    .toList();
            //便利结果集合
            list.forEach(item -> {
                //到数据库中查询是否有这个值,如果有这个值则把ID付给要保存的新值
                repository.findById(item.getId()).ifPresent(b -> item.setRivenId(b.getRivenId()));
                //增加|修改值
                repository.save(item);
                //增加|修改值的数量
                size.addAndGet(1);
            });
            log.info("共更新Warframe.Market紫卡武器 {} 条数据！", size);
            return size.get();
        } else {
            List<RivenItems> rivenItems = repository.saveAll(items);
            log.info("共更新Warframe.Market紫卡武器 {} 条数据！", rivenItems.size());
            return rivenItems.size();
        }
    }

    // 遗物
    public static Integer getRelics() {
        log.info("开始初始化Relics数据！");
        HttpUtils.Body body = HttpUtils.sendGet(ApiUrl.WARFRAME_RELICS_DATA);
        if (body.getCode().equals(HttpCodeEnum.SUCCESS)) {
            List<Relics> relics = JSON.parseObject(body.getBody()).getJSONArray("relics").toJavaList(Relics.class).stream().filter(r -> r.getState().equals("Intact")).toList();
            relics = relics.stream().peek(r -> r.setRewards(r.getRewards().stream().peek(w -> w.setRelics(r)).toList())).toList();
            RelicsRepository repository = SpringUtils.getBean(RelicsRepository.class);
            if (!repository.findAll().isEmpty()) {
                List<Relics> all = repository.findAll();
                List<Relics> list = relics.stream().filter(item ->
                                !all.stream()
                                        .collect(Collectors.toMap(Relics::toString, value -> value))
                                        .containsKey(item.toString())
                        )
                        .toList();
                relics = repository.saveAll(list);
                log.info("共更新Warframe 遗物表 {} 条数据！", relics.size());
                return relics.size();
            } else {
                relics = repository.saveAll(relics);
                log.info("共初始化Warframe 遗物表 {} 条数据！", relics.size());
                return relics.size();
            }

        }
        return -1;
    }

    //别名
    public static Integer getAlias() {
        log.info("开始初始化别名数据！");
        try {
            List<Alias> aliasList = JSON.parseArray(new File(DATA_SOURCE_PATH + "alias.json").toURI().toURL(), JSONReader.Feature.SupportSmartMatch).toJavaList(Alias.class);
            AliasRepository aliasR = SpringUtils.getBean(AliasRepository.class);
            if (!aliasR.findAll().isEmpty()) {
                List<Alias> all = aliasR.findAll();
                List<Alias> list = aliasList.stream().filter(item ->
                                !all.stream()
                                        .collect(Collectors.toMap(Alias::toString, value -> value))
                                        .containsKey(item.toString())
                        )
                        .toList();
                aliasList = aliasR.saveAll(list);
                log.info("共更新Warframe别名表 {} 条数据！", aliasList.size());
                return aliasList.size();
            } else {
                aliasList = aliasR.saveAll(aliasList);
                log.info("共初始化Warframe别名表 {} 条数据！", aliasList.size());
                return aliasList.size();
            }
        } catch (MalformedURLException e) {
            log.warn("别名表初始化错误！{}", e.getMessage());
            return -1;
        }
    }

    // 紫卡词条
    public static Integer getRivenTion() {
        log.info("开始初始化紫卡词条参数数据！");
        try {
            List<RivenTion> rivenTions = JSON.parseArray(new File(DATA_SOURCE_PATH + "market_riven_tion.json").toURI().toURL(), JSONReader.Feature.SupportSmartMatch).toJavaList(RivenTion.class);
            RivenTionRepository records = SpringUtils.getBean(RivenTionRepository.class);
            if (!records.findAll().isEmpty()) {
                List<RivenTion> all = records.findAll();
                List<RivenTion> list = rivenTions.stream().filter(item ->
                                !all.stream()
                                        .collect(Collectors.toMap(RivenTion::toString, value -> value))
                                        .containsKey(item.toString())
                        )
                        .toList();
                rivenTions = records.saveAll(list);
                log.info("共更新Warframe紫卡词条参数表 {} 条数据！", rivenTions.size());
                return rivenTions.size();
            } else {
                rivenTions = records.saveAll(rivenTions);
                log.info("共初始化Warframe紫卡词条参数表 {} 条数据！", rivenTions.size());
                return rivenTions.size();
            }
        } catch (MalformedURLException e) {
            log.warn("紫卡词条参数表初始化错误！{}", e.getMessage());
            return -1;
        }
    }

    // 紫卡词条别名
    public static Integer getRivenTionAlias() {
        log.info("开始初始化紫卡词条别名数据！");
        try {
            List<RivenTionAlias> rivenTionAliases = JSON.parseArray(new File(DATA_SOURCE_PATH + "market_riven_tion_alias.json").toURI().toURL(), JSONReader.Feature.SupportSmartMatch).toJavaList(RivenTionAlias.class);
            RivenTionAliasRepository repository = SpringUtils.getBean(RivenTionAliasRepository.class);
            if (!repository.findAll().isEmpty()) {
                List<RivenTionAlias> all = repository.findAll();
                List<RivenTionAlias> list = rivenTionAliases.stream().filter(item ->
                                !all.stream()
                                        .collect(Collectors.toMap(RivenTionAlias::toString, value -> value))
                                        .containsKey(item.toString())
                        )
                        .toList();
                rivenTionAliases = repository.saveAll(list);
                log.info("共更新Warframe紫卡词条别名表 {} 条数据！", rivenTionAliases.size());
                return rivenTionAliases.size();
            } else {
                rivenTionAliases = repository.saveAll(rivenTionAliases);
                log.info("共初始化Warframe紫卡词条别名表 {} 条数据！", rivenTionAliases.size());
                return rivenTionAliases.size();
            }

        } catch (MalformedURLException e) {
            log.warn("紫卡词条别名表初始化错误！{}", e.getMessage());
            return -1;
        }
    }

    //翻译
    public static Integer initTranslation() {
        log.info("开始初始化翻译数据！");
        try {
            List<Translation> translations = JSON.parseArray(new File(DATA_SOURCE_PATH + "translation.json").toURI().toURL(), JSONReader.Feature.SupportSmartMatch).toJavaList(Translation.class);
            TranslationRepository t = SpringUtils.getBean(TranslationRepository.class);
            if (!t.findAll().isEmpty()) {
                List<Translation> all = t.findAll();
                List<Translation> list = translations.stream().filter(item ->
                        !all.stream()
                                .collect(Collectors.toMap(m -> m.getCn() + m.getEn() + m.getIsSet() + m.getIsPrime(), value -> value))
                                .containsKey(item.getCn() + item.getEn() + item.getIsSet() + item.getIsPrime())).toList();
                translations = t.saveAll(list);
                log.info("共更新Warframe翻译表 {} 条数据！", translations.size());
                return translations.size();
            } else {
                translations = t.saveAll(translations);
                log.info("共初始化Warframe翻译表 {} 条数据！", translations.size());
                return translations.size();
            }
        } catch (MalformedURLException e) {
            log.warn("翻译表初始化错误！{}", e.getMessage());
            return -1;
        }
    }

    //紫卡计算器数据
    public static Integer getRivenAnalyseTrend() {
        log.info("开始初始化紫卡计算器数据！");
        try {
            List<RivenAnalyseTrend> translations = JSON.parseArray(new File(DATA_SOURCE_PATH + "riven_analyse_trend.json").toURI().toURL(), JSONReader.Feature.SupportSmartMatch).toJavaList(RivenAnalyseTrend.class);
            RivenAnalyseTrendRepository r = SpringUtils.getBean(RivenAnalyseTrendRepository.class);
            if (!r.findAll().isEmpty()) {
                List<RivenAnalyseTrend> all = r.findAll();
                List<RivenAnalyseTrend> list = translations.stream().filter(item ->
                        !all.stream().collect(Collectors.toMap(RivenAnalyseTrend::toString, value -> value))
                                .containsKey(item.toString())).toList();
                translations = r.saveAll(list);
                log.info("共更新紫卡计算器表 {} 条数据！", translations.size());
                return translations.size();
            } else {
                translations = r.saveAll(translations);
                log.info("共初始化紫卡计算器表 {} 条数据！", translations.size());
                return translations.size();
            }
        } catch (MalformedURLException e) {
            log.warn("紫卡计算器表初始化错误！{}", e.getMessage());
            return -1;
        }
    }

    public static Integer getRivenTrend() {
        log.info("开始初始化紫卡倾向数据！");
        try {
            List<RivenTrend> translations = JSON.parseArray(new File(DATA_SOURCE_PATH + "riven_trend.json").toURI().toURL(), JSONReader.Feature.SupportSmartMatch).toJavaList(RivenTrend.class);
            RivenTrendRepository r = SpringUtils.getBean(RivenTrendRepository.class);
            if (!r.findAll().isEmpty()) {
                List<RivenTrend> all = r.findAll();
                List<RivenTrend> list = translations.stream().filter(item ->
                        !all.stream().collect(Collectors.toMap(RivenTrend::toString, value -> value))
                                .containsKey(item.toString())).toList();
                translations = r.saveAll(list);
                log.info("共更新紫卡倾向表 {} 条数据！", translations.size());
                return translations.size();
            } else {
                translations = r.saveAll(translations);
                log.info("共初始化紫卡倾向表 {} 条数据！", translations.size());
                return translations.size();
            }
        } catch (MalformedURLException e) {
            log.warn("紫卡倾向表初始化错误！{}", e.getMessage());
            return -1;
        }
    }

    public static Boolean cloneDataSource() {
        log.info("开始初始化数据模板！");
        boolean flag = true;
        for (String url : ApiUrl.DATA_SOURCE_GIT) {
            try {
                JgitUtil git = JgitUtil.Build(url, "");
                git.pull();
                flag = false;
                break;
            } catch (Exception ignored) {
            }
        }
        return flag;
    }

}
