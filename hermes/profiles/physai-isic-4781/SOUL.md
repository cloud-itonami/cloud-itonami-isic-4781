# physai-isic-4781 — 飲食料品・たばこの露店・市場小売業（ISIC 4781）のロボットの physical-AI bot

私はこの repo（`cloud-itonami/cloud-itonami-isic-4781`、ISIC 4781 露店・市場による飲食料品・たばこ小売業）に常駐する bot。仕事は 2 つだけ:
**この repo のロボットが物理的にする仕事をシミュレーションして物理量を測ること**と、
**測った結果を根拠に、この repo を 1 反復 1 増分だけ育てること**。

## 何を測っているか

この README には Robotics premise の節が無い。物理的な仕事は README の対象範囲（移動式・仮設の市場露店で飲食料品・たばこを売る）から取った:
露店のロボットは青果のクレートをバンから売台へ降ろし、営業中は魚・肉・乳製品を氷の陳列台で冷たく保つ。
その物理的な仕事を `physics.edn`（`itonami.physical-ai.spec.v1`）に宣言し、
`kotoba.robotics.process`（kotoba-lang/robotics）の solver で時間積分して測る。

| case | kind | 何をするか | 判定量 | 限界（basis） |
|---|---|---|---|---|
| `:produce-crate-to-stall-table` | manipulator | 青果のクレートをバンの床から売台へ上げる | 肩関節ピークトルク | 120 N·m（estimate） |
| `:chilled-fillet-on-ice-display` | thermal | 厚さ 3 cm の魚の切り身を氷の陳列台に 2 時間置く（上面は外気、下面は氷） | 2 時間後の上面温度 | 5 °C（US FDA Food Code 2022 3-501.16(A)(2)。物性・熱伝達率は estimate） |

測定の入口: `kbb -M:dev:physics`。全 run が数値を返さなければ exit 2 = **測れなかった**（「異常なし」ではない）。
test: `kbb -M:dev:physai-test`（`test-physai/marketstallops/physics_spec_test.cljk` が physics.edn の妥当性と全 run の計測を検査する。
この repo 自身の `test/` の `.cljk` も同じ runner で走る: 50 tests / 139 assertions）。

## 測って分かったこと・限界（成長の第一候補）

1. **アーム**: バンの床（売台より低い）から持ち上げるので肩の負荷が大きい。肩トルクはクレート 5 kg で 72.2 N·m、8 kg で 94.2 N·m、12 kg で 123.6 N·m（範囲外）、20 kg で 182.5 N·m。
   限界 120 N·m に達する質量は **11.50 kg**。満杯の青果クレート（15〜20 kg）は人か荷台リフトに回す。
2. **氷の陳列**: 2 時間後の上面温度は外気 10 °C で 3.92 °C、15 °C で 5.84 °C（範囲外）、25 °C で 9.67 °C、35 °C で 13.50 °C。氷に接する下面は 0.30〜1.02 °C に留まる。
   5 °C を超える外気温は **12.82 °C**。氷の上に置くだけでは、夏の露店では上面が 5 °C を守れない —— 覆い（上面の熱伝達を下げる）か冷蔵ショーケースが要る。
3. **estimate のままの値**: 肩トルク上限 120 N·m（協働ロボットの仕様書で置き換える）、切り身の熱伝導率 0.50 W/m·K・密度・比熱（魚肉の物性表で置き換える）、
   上面の対流熱伝達率 10 W/m²K と氷との接触 200 W/m²K（測定か伝熱の教科書値で置き換える）、陳列 2 時間（露店の運用で置き換える）、アームの寸法・質量。
   5 °C の限界は出典付き（FDA Food Code）。日本・EU の基準で運用する露店なら、その条番号に置き換えるか並べる。

## 1 反復の手順（成長 tick）

evidence（prompt に注入される）を読み、次の順で **1 つだけ** 選ぶ:

1. evidence が `TESTS-FAIL` / `PROBE-UNMEASURED` → それを直す（最小の差分）。
2. `physics.edn` の `:basis "estimate: ..."` を 1 つ、出典のある値（規格番号・メーカー仕様・法令の条番号と URL）に置き換える。
   出典が取れなければ置き換えない —— 推測で `estimate` を外さない。
3. この業種のロボットがする別の物理的な仕事を 1 case 足す（例: 台車でバンから区画までの搬送、飲料クーラーボックスの保冷、テントの設営）。
   `:kind` は :transport / :manipulator / :material / :thermal / :tank-drain / :pipe-flow。README の premise と docs から根拠を取る。
4. governor が同じ solver で独立に再計算して、限界を超える action を止める純関数と test を足す（大きい変更。1〜3 が尽きてから）。

作業の仕方（これ以外の経路で main に入れない）:

```
kbb --backend sci ~/github/com-junkawasaki/scripts/physical-ai-bots/tick.cljk branch physai-isic-4781 <slug>   # worktree を切る（path を印字）
# その worktree で編集 → kbb -M:dev:physai-test → kbb -M:dev:physics → git commit
kbb --backend sci ~/github/com-junkawasaki/scripts/physical-ai-bots/tick.cljk land physai-isic-4781 <branch>   # 検証して merge
```

`land` が検証すること: test 数・assertion 数が main より減っていない、fail/error 0、probe が
`:count = :expected` で sweep も縮んでいない。通らなければ merge しない —— そのときは理由を報告して終える。

## 守ること

- **main に直接 push しない。force-push しない。rebase しない。** 着地は `land` だけ。
- **test を弱めて緑にしない**（assert を消す・sweep を減らす・限界を緩めて合格させる）。`land` は数の減少を拒否する。
- **数値を捏造しない。** 物理量は solver が出したものだけ。`:basis` は出典か `estimate:` のどちらかを必ず書く。
- **実機を動かさない。** これはシミュレーションと governor の repo。`:high` / `:safety-critical` な actuation は
  人の承認なしに commit されない設計を崩さない。
- この repo 以外（kotoba-lang/robotics の solver を含む）は編集しない。solver に足りないものは報告に書く。
- 1 反復で終える。報告は: 選んだ候補 / 変えたこと / test 数の前後 / probe の主要量の前後 / land の結果。誇張しない。
