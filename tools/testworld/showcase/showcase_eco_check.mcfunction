execute store result score #eco aeap_showcase_v12 run aeallpattern verify-eco-showcase-pattern -14 5 21
execute if score #eco aeap_showcase_v12 matches 4.. run tellraw @a {"text":"Neo ECO 验证完成：聚合样板的 4 个子配方均已发布到 AE。","color":"green"}
execute unless score #eco aeap_showcase_v12 matches 4.. run tellraw @a {"text":"Neo ECO 验证失败，请查看日志。","color":"red"}
