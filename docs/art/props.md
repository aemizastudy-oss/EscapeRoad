# 시역 일러스트 사양 (소지품·장면 소품)

배경과 인물은 일러스트로 바뀌었지만 소지품과 장면 소품은 아직 코드로 그린 평면 그림이라 함께 놓이면 어색하다. 아래 이름의 그림 파일을 `app/src/main/res/drawable-nodpi/`에 넣고 다시 빌드하면 앱이 자동으로 그 그림을 쓴다. 파일이 없는 것은 지금 그림이 그대로 나온다. 코드는 바꿀 필요가 없다.

## 공통 사양

| 항목 | 값 |
| --- | --- |
| 크기 | 512 × 512 px, 정사각형 |
| 형식 | PNG 또는 WEBP, **배경 투명** |
| 구도 | 물건 하나를 가운데에, 위·아래·좌·우 여백 약 8%. 살짝 위에서 내려다본 3/4 시점 |
| 그림자 | 바닥 그림자를 물건 아래에 옅게 포함 |
| 화풍 | 장면 배경과 같은 화풍: 반실사 셀 셰이딩, 따뜻한 등불빛(왼쪽 위 광원), 어두운 조선 미스터리 분위기 |
| 쓰이는 곳 | 소지품 칸(64 dp), 자세히 보기(260 dp), 새 소지품 창(150 dp), 장면 소품(약 50~120 dp) |

작게 보여도 알아볼 수 있도록 윤곽이 또렷하고 디테일은 적당히 줄인다. 글자가 들어가는 물건(열쇠패 乙, 밀서 봉인 密)은 그 글자만 크게 넣는다.

### 이미지 생성 도구용 공통 문구

> Single object game item icon, 1:1, transparent background, centered with small margin, three-quarter top view, semi-realistic cel-shaded illustration, warm lantern light from upper left, soft drop shadow, Joseon dynasty 1795 mystery horror mood, consistent item set, no text except where specified.

## 1. 소지품 (`item_<id>`)

| 파일 이름 | 물건 | 덧붙일 설명 |
| --- | --- | --- |
| `item_secret_letter.png` | 밀서 | folded hanji letter tied with string, red wax seal stamped with the hanja 密 |
| `item_recipe_first.png` | 비방 첫 장 | torn old hanji page with brush-written medical recipe, small herb sketch, torn bottom edge |
| `item_notebook.png` | 연이의 수첩 | small Korean thread-bound book (five red stitches on the spine), dark indigo cover with a paper title slip, water stain |
| `item_key_eul.png` | 서고 열쇠패 乙 | wooden key tag with visible grain, the hanja 乙 carved deep, red silk tassel on top |
| `item_bojagi.png` | 청포 보자기 | blue cloth bundle (bojagi) knotted at the top with two ears |
| `item_strong_water.png` | 흐르는 물 한 병 | celadon gourd-shaped bottle with wave pattern, wooden stopper, red cord |
| `item_grain.png` | 곡식 자루 | burlap grain sack tied with rope, a few rice grains spilled |
| `item_flint.png` | 부싯돌 | grey flint stone and a C-shaped iron striker, a few sparks |
| `item_news.png` | 「고을 소식」 | old newspaper sheet pieced together from six torn strips with tape, small red number 4172 in a corner |
| `item_licorice.png` | 감초 | bundle of licorice roots tied with red string, a few cut slices showing yellow cores |
| `item_vaccine.png` | 완성한 백신 | small white porcelain medicine bottle with blue painted pattern, red cloth stopper, faint teal glow |

## 2. 장면 소품 (`item_<id>`)

장면 위에 올라가는 물건이다. 소지품과 같은 사양이지만 배경 위에서 작게 보이므로 실루엣이 특히 또렷해야 한다.

| 파일 이름 | 물건 | 쓰이는 장면 | 덧붙일 설명 |
| --- | --- | --- | --- |
| `item_bowl.png` | 깨진 사발 | 화성행궁 | cracked white ceramic bowl with a dried black stain, one shard beside it |
| `item_paper_scrap.png` | 접힌 쪽지 | 여러 장면 | small folded hanji note with faint brush writing |
| `item_dial_box.png` | 수문지기의 함 | 화홍문 | small wooden box with two brass rotary dials marked with zodiac hours |
| `item_chest.png` | 창고 궤짝 | 화홍문 | old wooden chest with brass corners and a lock plate with a carved slot |
| `item_gate_wheel.png` | 판문 도르래 · 수문 물레 | 화홍문, 만석거 | wooden sluice gate wheel on a small frame, water underneath |
| `item_stele.png` | 깨진 비석 | 만석거 | small broken stone stele with the hanja 萬石 carved |
| `item_beacon_box.png` | 봉화함 | 박물관 | dark red wooden box with five small fire holes on top and a brass latch |
| `item_pot.png` | 약탕기 | 박물관 | clay medicine pot on a small brazier, steam rising |
| `item_sign.png` | 호숫가 안내판 | 호수공원 | modern park signboard showing two lakes with their names erased (question marks) |
| `item_bottles.png` | 약병 선반 | 호수공원 | four small numbered medicine bottles with red stoppers on a short wooden shelf |
| `item_shadow_figure.png` | 물가의 그림자 | 호수공원 | eerie dark human silhouette with long loose hair, faint pale eyes, standing still, no gore |

## 넣는 방법

1. 파일을 `app/src/main/res/drawable-nodpi/`에 복사한다(예: `item_notebook.png`). 같은 이름의 `.png`와 `.webp`를 둘 다 두면 빌드가 실패한다.
2. 다시 빌드·설치한다: `./gradlew installDebug`
3. 장면 소품은 크기와 위치가 장면 그림과 맞는지 `ExploreSceneCaptureTest` 캡처로 확인한다. 위치는 `game/SiyeokFlow5.kt`의 조사할 곳 좌표에서 고친다.
