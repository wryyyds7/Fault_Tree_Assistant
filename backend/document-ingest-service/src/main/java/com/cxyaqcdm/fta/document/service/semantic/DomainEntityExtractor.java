package com.cxyaqcdm.fta.document.service.semantic;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@Slf4j
public class DomainEntityExtractor {

    private static final Map<SemanticParagraph.EntityType, List<Pattern>> ENTITY_PATTERNS = new HashMap<>();
    private static final Map<SemanticParagraph.EntityType, Set<String>> DOMAIN_DICTIONARY = new HashMap<>();

    static {
        initPatterns();
        initDomainDictionary();
    }

    private static void initPatterns() {
        ENTITY_PATTERNS.put(SemanticParagraph.EntityType.FAULT_PHENOMENON, Arrays.asList(
                Pattern.compile("[\\u4e00-\\u9fa5]{2,8}(?:故障|失效|损坏|异常|报警|报错|停止运行|卡死|烧毁|泄漏)"),
                Pattern.compile("(?:无法启动|不能启动|启动失败|运行异常|工作异常|响应迟缓|功能丧失)"),
                Pattern.compile("(?:压力[高低]|温度[高低]|流量[大小]|转速[高低]|电压[高低]|电流[大小])")
        ));

        ENTITY_PATTERNS.put(SemanticParagraph.EntityType.FAULT_CAUSE, Arrays.asList(
                Pattern.compile("(?:由于|因为|起因于|来源于|由.*引起|原因.*是)"),
                Pattern.compile("(?:老化|磨损|腐蚀|疲劳|裂纹|松动|堵塞|污染|过热|过载|短路|断路)"),
                Pattern.compile("(?:设计缺陷|制造缺陷|安装不当|操作失误|维护不当|保养不足)")
        ));

        ENTITY_PATTERNS.put(SemanticParagraph.EntityType.FAULT_MODE, Arrays.asList(
                Pattern.compile("[\\u4e00-\\u9fa5]{2,6}(?:模式|形式|类型|方式)"),
                Pattern.compile("(?:渐变故障|突发故障|间歇性故障|完全性故障|部分性故障)"),
                Pattern.compile("(?:短路模式|开路模式|泄漏模式|卡滞模式|偏移模式)")
        ));

        ENTITY_PATTERNS.put(SemanticParagraph.EntityType.COMPONENT, Arrays.asList(
                Pattern.compile("[\\u4e00-\\u9fa5]{2,10}(?:阀|泵|电机|传感器|继电器|接触器|开关|管道|容器|过滤器|密封件|轴承|齿轮|弹簧)"),
                Pattern.compile("(?:液压站|油箱|冷却系统|润滑系统|控制系统|驱动系统|传感系统)"),
                Pattern.compile("[A-Z]{2,5}(?:阀|泵|电机|M|Valve|Pump|Motor|Sensor)")
        ));

        ENTITY_PATTERNS.put(SemanticParagraph.EntityType.CONDITION, Arrays.asList(
                Pattern.compile("(?:当.*时|若.*则|在.*条件下|满足.*条件)"),
                Pattern.compile("(?:压力高于|压力低于|温度高于|温度低于|流量大于|流量小于|转速超过)"),
                Pattern.compile("(?:额定值|设定值|阈值|门限|临界值)")
        ));
    }

    private static void initDomainDictionary() {
        DOMAIN_DICTIONARY.put(SemanticParagraph.EntityType.FAULT_PHENOMENON, new HashSet<>(Arrays.asList(
                "泄漏", "堵塞", "卡死", "烧毁", "报警", "停机", "失效", "异常", "故障", "损坏", "断裂", "变形", "腐蚀", "磨损"
        )));

        DOMAIN_DICTIONARY.put(SemanticParagraph.EntityType.COMPONENT, new HashSet<>(Arrays.asList(
                "阀门", "泵", "电机", "传感器", "继电器", "接触器", "开关", "变压器", "电缆", "管道", "容器", "过滤器",
                "密封件", "轴承", "齿轮", "弹簧", "液压缸", "油缸", "气缸", "电磁阀", "安全阀", "止回阀", "调节阀",
                "齿轮泵", "叶片泵", "柱塞泵", "离心泵", "螺杆泵", "直流电机", "交流电机", "伺服电机", "步进电机"
        )));

        DOMAIN_DICTIONARY.put(SemanticParagraph.EntityType.FAULT_CAUSE, new HashSet<>(Arrays.asList(
                "老化", "磨损", "腐蚀", "疲劳", "裂纹", "松动", "污染", "过热", "过载", "短路", "断路", "接地",
                "设计缺陷", "制造缺陷", "安装不当", "操作失误", "维护不当", "保养不足", "材质问题", "工艺问题"
        )));

        DOMAIN_DICTIONARY.put(SemanticParagraph.EntityType.FAULT_MODE, new HashSet<>(Arrays.asList(
                "完全失效", "部分失效", "渐变失效", "突发失效", "间歇失效", "功能失效", "性能下降"
        )));

        DOMAIN_DICTIONARY.put(SemanticParagraph.EntityType.CONDITION, new HashSet<>(Arrays.asList(
                "高压", "低压", "高温", "低温", "高速", "低速", "过载", "欠压", "过流", "欠流", "短路", "开路"
        )));
    }

    public List<SemanticParagraph.DomainEntity> extractEntities(String text) {
        List<SemanticParagraph.DomainEntity> entities = new ArrayList<>();

        for (Map.Entry<SemanticParagraph.EntityType, List<Pattern>> entry : ENTITY_PATTERNS.entrySet()) {
            SemanticParagraph.EntityType type = entry.getKey();
            for (Pattern pattern : entry.getValue()) {
                Matcher matcher = pattern.matcher(text);
                while (matcher.find()) {
                    SemanticParagraph.DomainEntity entity = SemanticParagraph.DomainEntity.builder()
                            .text(matcher.group())
                            .type(type)
                            .startPos(matcher.start())
                            .endPos(matcher.end())
                            .confidence(calculateConfidence(matcher.group(), type))
                            .build();
                    entities.add(entity);
                }
            }
        }

        for (Map.Entry<SemanticParagraph.EntityType, Set<String>> entry : DOMAIN_DICTIONARY.entrySet()) {
            SemanticParagraph.EntityType type = entry.getKey();
            for (String keyword : entry.getValue()) {
                int pos = 0;
                while ((pos = text.indexOf(keyword, pos)) != -1) {
                    final int currentPos = pos;
                    boolean alreadyCovered = entities.stream()
                            .anyMatch(e -> currentPos >= e.getStartPos() && currentPos < e.getEndPos());
                    if (!alreadyCovered) {
                        SemanticParagraph.DomainEntity entity = SemanticParagraph.DomainEntity.builder()
                                .text(keyword)
                                .type(type)
                                .startPos(pos)
                                .endPos(pos + keyword.length())
                                .confidence(0.8)
                                .build();
                        entities.add(entity);
                    }
                    pos += keyword.length();
                }
            }
        }

        entities.sort(Comparator.comparingInt(SemanticParagraph.DomainEntity::getStartPos));

        return mergeOverlappingEntities(entities);
    }

    private double calculateConfidence(String matchedText, SemanticParagraph.EntityType type) {
        double baseConfidence = 0.7;
        int textLength = matchedText.length();

        if (textLength >= 4 && textLength <= 10) {
            baseConfidence += 0.15;
        } else if (textLength > 10) {
            baseConfidence += 0.1;
        } else {
            baseConfidence -= 0.1;
        }

        return Math.min(0.95, baseConfidence);
    }

    private List<SemanticParagraph.DomainEntity> mergeOverlappingEntities(List<SemanticParagraph.DomainEntity> entities) {
        if (entities.isEmpty()) {
            return entities;
        }

        List<SemanticParagraph.DomainEntity> merged = new ArrayList<>();
        SemanticParagraph.DomainEntity current = entities.get(0);

        for (int i = 1; i < entities.size(); i++) {
            SemanticParagraph.DomainEntity next = entities.get(i);
            if (next.getStartPos() <= current.getEndPos()) {
                if (next.getConfidence() > current.getConfidence()) {
                    current = next;
                }
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);

        return merged;
    }

    public Map<SemanticParagraph.EntityType, Long> getEntityTypeDistribution(List<SemanticParagraph.DomainEntity> entities) {
        return entities.stream()
                .collect(Collectors.groupingBy(SemanticParagraph.DomainEntity::getType, Collectors.counting()));
    }
}