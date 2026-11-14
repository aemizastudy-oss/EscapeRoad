# 시역 일러스트 사양 (인물 초상·장면 배경)

그림 파일을 아래 이름으로 `app/src/main/res/drawable-nodpi/`에 넣고 다시 빌드하면 앱이 자동으로 그 그림을 씁니다. 파일이 없는 인물·장소는 지금처럼 코드로 그린 그림이 나옵니다. 코드는 바꿀 필요가 없습니다.

- 파일 이름은 **영문 소문자·숫자·밑줄만** 씁니다(안드로이드 리소스 규칙).
- 같은 이름의 `.png`·`.jpg`·`.webp`를 둘 이상 두면 빌드가 실패합니다. 하나만 넣으세요.
- 그림의 저작권·사용권이 팀에 있는지 확인하세요. 실존 인물(정조, 혜경궁 등)을 닮게 그리지 않습니다. 인물은 모두 허구입니다.

## 1. 인물 초상

| 항목 | 값 |
| --- | --- |
| 크기 | 900 × 1260 px (가로:세로 = 5:7) |
| 형식 | PNG, **배경 투명** |
| 구도 | 가슴 위(바스트 숏). 어깨가 그림 아래 끝에 닿게. 머리 꼭대기(갓·모자 포함) 위로 여백 약 5% |
| 방향 | 몸은 정면에서 오른쪽으로 15° 정도. 화면 왼쪽에 서서 오른쪽 대사 상자를 보는 자리 |
| 화면 표시 | 장면 화면 왼쪽 아래 270 × 378 dp, 아래 1/5은 대사 상자에 가려질 수 있음 |
| 화풍 | 여덟 명 모두 같은 화풍. 어두운 조선 미스터리(시역) 분위기, 반실사 웹툰 또는 수묵 채색풍. 조명은 왼쪽 위에서, 배경색과 잘 붙게 가장자리는 약간 어둡게 |

| 파일 이름 | 인물 | 생김새·옷차림 | 표정·분위기 |
| --- | --- | --- | --- |
| `portrait_physician.png` | 서도윤 · 내의원 의원 (30대 초반 남성) | 검은 갓(넓은 챙, 갓끈), 짙은 남색 도포, 흰 동정. 단정한 콧수염, 손에 약첩 또는 일지 | 침착하지만 지친 눈, 무언가 숨기는 듯한 굳은 입매 |
| `portrait_yeoni.png` | 연이 · 행궁 궁녀 (10대 후반 여성) | 쪽 진 머리, 은비녀, 붉은 댕기. 연둣빛 저고리, 붉은 고름 | 창백한 얼굴, 푸르스름한 입술, 겁먹은 눈. 병세가 보이되 잔인하지 않게 |
| `portrait_hancheol.png` | 한철 · 장용영 병사 (20대 후반 남성) | 검은 전립에 붉은 상모, 남색 군복에 붉은 소매(동달이), 허리에 환도 | 엄하고 단단한 표정, 밤샘의 피로 |
| `portrait_park.png` | 박 서방 · 화홍문 수문지기 (40대 남성) | 상투, 흰 수건 머리띠, 해진 무명 저고리, 그을린 피부 | 억울하고 겁먹은 표정 |
| `portrait_kim.png` | 김씨 · 둔전 노인 (70대 남성) | 패랭이, 흰 수염과 흰 눈썹, 낡은 흰 옷 | 주름진 얼굴, 따뜻하지만 경계하는 눈 |
| `portrait_curator.png` | 학예사 · 2026년 박물관 (30대 여성) | 단발, 동그란 안경, 베이지 카디건과 흰 셔츠, 출입증 목걸이, 흰 면장갑 | 호기심 많고 차분한 표정 |
| `portrait_agency.png` | 조사국 · 시간 조사국 본부 | 사람 대신 상징: 청록빛 원형 홀로그램 문장 안의 모래시계. 또는 얼굴이 그림자에 가린 헤드셋 쓴 요원 | 차갑고 기계적인 느낌 |

### 이미지 생성 도구용 설명문 (예시)

모든 인물에 공통으로 앞에 붙이세요.

> Bust portrait, 5:7 vertical, transparent background, semi-realistic Korean webtoon style with ink-wash texture, Joseon dynasty 1795, dark mystery horror mood, soft rim light from upper left, body turned slightly to the right, shoulders touching the bottom edge, consistent character design series.

| 인물 | 덧붙일 설명 |
| --- | --- |
| 서도윤 | a calm but exhausted royal court physician in his early 30s, black wide-brimmed gat hat with strings, dark navy dopo robe with white collar, neat thin mustache, holding a folded medical journal, guarded tight-lipped expression |
| 연이 | a pale teenage palace maid, hair in a low chignon with a silver binyeo hairpin and red daenggi ribbon, light green jeogori with red ribbon tie, faint bluish lips, frightened eyes, eerie but not gory |
| 한철 | a stern Joseon royal guard soldier in his late 20s, black jeonlip hat with red horsehair tassel, navy uniform with red sleeves, sword hilt at waist, tired from night watch |
| 박 서방 | a weathered sluice gate keeper in his 40s, topknot, white cloth headband, worn cotton clothes, tanned skin, anxious defensive expression |
| 김씨 | an old farmer in his 70s, bamboo paeraengi hat, long white beard and eyebrows, worn white hanbok, kind but wary eyes |
| 학예사 | a modern Korean museum curator in her 30s, bob haircut, round glasses, beige cardigan over white shirt, ID lanyard, white cotton gloves, curious calm expression (2026, not Joseon) |
| 조사국 | a glowing teal circular hologram emblem with an hourglass in the center, sci-fi time agency insignia, no human face |

## 2. 장면 배경 (선택)

| 항목 | 값 |
| --- | --- |
| 크기 | 1920 × 1080 px (16:9) |
| 형식 | JPG 또는 WEBP(용량을 줄이려면 WEBP) |
| 구도 | 아래 40%는 대사 상자와 인물이 놓이므로 비교적 단순하게. 앱이 아래쪽을 자동으로 어둡게 깐다 |

| 파일 이름 | 장면 |
| --- | --- |
| `scene_agency.jpg` | 2026년 시간 조사국 지하 관제실. 벽면 지도에 수원 일대가 붉게 깜빡임 |
| `scene_time_rift.jpg` | 시간 이동. 금빛 동심원과 거꾸로 도는 시계 |
| `scene_palace_dusk.jpg` | 1795년 해 질 녘 화성행궁 봉수당, 등불 |
| `scene_palace_night.jpg` | 같은 행궁의 밤, 보름달, 담장 아래 그림자 |
| `scene_sluice_night.jpg` | 밤의 화홍문 일곱 홍예와 수원천, 횃불, 판문 |
| `scene_reservoir_dawn.jpg` | 새벽 만석거 둑과 영화정, 둑 위로 피한 마을 사람들 |
| `scene_museum.jpg` | 2026년 박물관 수장고·진열장, 찢어진 문서 |
| `scene_lake_night.jpg` | 1795년 해 질 녘 아랫방죽. 한옥·초가 마을, 둑길 등불 (마지막 탐색 장면) |
| `scene_lake_modern.jpg` | 2026년 밤의 원천호수, 아파트 불빛과 산책로 (5장 도착 첫 장면, 배드 엔딩) |

> 2026-11-14: 인물 초상 7장과 장면 배경 8장을 모두 넣었다(`.webp`). 배경을 다시 그리면 조사할 곳 좌표(`game/SiyeokFlow5.kt`)도 그림에 맞게 고쳐야 한다. `ExploreSceneCaptureTest`로 확인한다.

## 3. 넣는 방법

1. 파일을 `app/src/main/res/drawable-nodpi/`에 복사합니다(예: `portrait_physician.png`).
2. 앱을 다시 빌드·설치합니다: `./gradlew installDebug`
3. 프롤로그나 장소 도착 장면에서 확인합니다. 그림이 안 바뀌면 파일 이름 철자를 확인하세요.
