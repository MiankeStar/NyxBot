package com.nyx.bot.controller.api.html.warframe.mission;

import com.nyx.bot.enums.WarframeMissionTypeEnum;
import com.nyx.bot.exception.DataNotInfoException;
import com.nyx.bot.repo.impl.warframe.TranslationService;
import com.nyx.bot.res.GlobalStates;
import com.nyx.bot.utils.CacheUtils;
import com.nyx.bot.utils.DateUtils;
import com.nyx.bot.utils.StringUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

@Slf4j
@Controller
@RequestMapping("/private")
public class FissuesHtmlController {

    @Resource
    TranslationService trans;

    @NotNull
    private static List<GlobalStates.Fissures> getFissures(Integer type) throws DataNotInfoException {
        GlobalStates sgs = CacheUtils.getGlobalState();
        List<GlobalStates.Fissures> fissures = sgs.getFissures();
        List<GlobalStates.Fissures> list = new ArrayList<>();
        //分级
        switch (type) {
            //裂隙
            case 0 -> fissures.forEach(f -> {
                if (f.getActive()) {
                    if (!f.getIsStorm() && !f.getIsHard()) {
                        list.add(f);
                    }
                }
            });
            //九重天
            case 1 -> fissures.forEach(f -> {
                if (f.getActive()) {
                    if (f.getIsStorm()) {
                        list.add(f);
                    }
                }
            });
            //钢铁
            case 2 -> fissures.forEach(f -> {
                if (f.getActive()) {
                    if (f.getIsHard()) {
                        list.add(f);
                    }
                }
            });
            default -> {
            }
        }
        return list;
    }

    @GetMapping("/getFissuesHtml/{type}")
    public String getHtml(Model model, @PathVariable Integer type) throws DataNotInfoException {
        List<GlobalStates.Fissures> list = getFissures(type);
        //排序
        list.sort(Comparator.comparing(GlobalStates.Fissures::getTierNum));

        //翻译
        list.forEach(f -> {
            String node = f.getNode();
            if (f.getIsStorm()) {
                f.setNode(trans.enToZh(
                        StringUtils.substring(
                                node,
                                0,
                                node.indexOf('('))) +
                        "(" +
                        trans.enToZh(
                                StringUtils.substring(
                                        node,
                                        node.indexOf('('),
                                        node.indexOf(')')).replace("(", "").trim())
                        + "比邻星)");
            } else {
                f.setNode(trans.enToZh(
                        StringUtils.substring(
                                node,
                                0,
                                node.indexOf('('))) +
                        "(" +
                        trans.enToZh(
                                StringUtils.substring(
                                        node,
                                        node.indexOf('('),
                                        node.indexOf(')')).replace("(", "").trim())
                        + ")");
            }
            f.setMissionType(trans.enToZh(f.getMissionType()));
            f.setMissionKey(trans.enToZh(f.getMissionKey()));
            f.setTier(trans.enToZh(f.getTier()));
            f.setEta(DateUtils.getDiff(f.getExpiry(), new Date()));
        });
        model.addAttribute("type", type);
        model.addAttribute("fissues", list);
        return "html/fissues";
    }

    @PostMapping("/getSubscribeFissuesHtml")
    public String getSubscribeFissuesHtml(Model model, @RequestBody List<WarframeMissionTypeEnum> types) throws DataNotInfoException {
        GlobalStates sgs = CacheUtils.getGlobalState();
        List<GlobalStates.Fissures> fissures = sgs.getFissures();
        fissures = fissures.stream().filter(f -> types.stream().anyMatch(t -> f.getMissionType().toUpperCase().contains(t.name().toUpperCase()))).peek(f -> {
            String node = f.getNode();
            if (f.getIsStorm()) {
                f.setNode(trans.enToZh(
                        StringUtils.substring(
                                node,
                                0,
                                node.indexOf('('))) +
                        "(" +
                        trans.enToZh(
                                StringUtils.substring(
                                        node,
                                        node.indexOf('('),
                                        node.indexOf(')')).replace("(", "").trim())
                        + "比邻星)");
            } else {
                f.setNode(trans.enToZh(
                        StringUtils.substring(
                                node,
                                0,
                                node.indexOf('('))) +
                        "(" +
                        trans.enToZh(
                                StringUtils.substring(
                                        node,
                                        node.indexOf('('),
                                        node.indexOf(')')).replace("(", "").trim())
                        + ")");
            }
            f.setMissionType(trans.enToZh(f.getMissionType()));
            f.setMissionKey(trans.enToZh(f.getMissionKey()));
            f.setTier(trans.enToZh(f.getTier()));
            f.setEta(DateUtils.getDiff(f.getExpiry(), new Date()));
        }).sorted(Comparator.comparing(GlobalStates.Fissures::getTierNum)).toList();
        model.addAttribute("type", 0);
        model.addAttribute("fissues", fissures);

        return "html/fissues";
    }

    @GetMapping("/getSubscribeFissuesHtml/{type}/tierNum")
    public String getSubscribeFissuesHtml(Model model, String type, Long tierNum) {

        return "html/subscribeFissues";
    }


}
