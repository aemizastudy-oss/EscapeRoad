# 게임 리소스: 음성 · BGM · 폰트 · 그림

[← README로 돌아가기](../README.md)

「시역」에 실제로 들어간 리소스 파일 목록입니다. 출처와 라이선스 요약은 README의 [출처·라이선스](../README.md#10-출처라이선스)에 있습니다. 파일 이름이나 그림을 누르면 바로 열립니다. 음성·BGM은 깃허브 파일 화면에서 **View raw** 또는 다운로드로 들을 수 있습니다.

## 🎙️ 음성

| 구분 | 파일 | 설명 |
| --- | --- | --- |
| 프롤로그 녹음 17개 | [01](../app/src/main/res/raw/voice_prologue_01.mp3) · [02](../app/src/main/res/raw/voice_prologue_02.mp3) · [03](../app/src/main/res/raw/voice_prologue_03.mp3) · [04](../app/src/main/res/raw/voice_prologue_04.mp3) · [05](../app/src/main/res/raw/voice_prologue_05.mp3) · [06](../app/src/main/res/raw/voice_prologue_06.mp3) · [07](../app/src/main/res/raw/voice_prologue_07.mp3) · [08](../app/src/main/res/raw/voice_prologue_08.mp3) · [09](../app/src/main/res/raw/voice_prologue_09.mp3) · [10](../app/src/main/res/raw/voice_prologue_10.mp3) · [11](../app/src/main/res/raw/voice_prologue_11.mp3) · [12](../app/src/main/res/raw/voice_prologue_12.mp3) · [13](../app/src/main/res/raw/voice_prologue_13.mp3) · [14](../app/src/main/res/raw/voice_prologue_14.mp3) · [15](../app/src/main/res/raw/voice_prologue_15.mp3) · [16](../app/src/main/res/raw/voice_prologue_16.mp3) · [17](../app/src/main/res/raw/voice_prologue_17.mp3) | 팀이 만든 프롤로그 녹음. 녹음이 없는 대사는 Pleos TTS(ON_DEVICE)로 읽음 |
| 대본 | [script.md](voice/script.md) · [script.csv](voice/script.csv) | 장면별 대사와 파일 번호 |
| 주행 멘트 | [drive_lines.json](../app/src/main/assets/drive_lines.json) | 장소별 출발 안내와 "곧 도착합니다" 멘트(TTS로 읽음) |

## 🎵 BGM (21곡)

| 구간 | 파일 |
| --- | --- |
| 타이틀 · 조사국 · 시간 이동 | [bgm_title](../app/src/main/res/raw/bgm_title.mp3) · [bgm_agency](../app/src/main/res/raw/bgm_agency.mp3) · [bgm_time_rift](../app/src/main/res/raw/bgm_time_rift.mp3) |
| ① 화성행궁 | [bgm_palace_drive](../app/src/main/res/raw/bgm_palace_drive.mp3) · [bgm_palace_explore](../app/src/main/res/raw/bgm_palace_explore.mp3) · [bgm_palace_wall](../app/src/main/res/raw/bgm_palace_wall.mp3) |
| ② 화홍문 | [bgm_sluice_drive](../app/src/main/res/raw/bgm_sluice_drive.mp3) · [bgm_sluice_explore](../app/src/main/res/raw/bgm_sluice_explore.mp3) |
| ③ 만석거 | [bgm_reservoir_drive](../app/src/main/res/raw/bgm_reservoir_drive.mp3) · [bgm_reservoir_explore](../app/src/main/res/raw/bgm_reservoir_explore.mp3) |
| ④ 수원광교박물관 | [bgm_museum_drive](../app/src/main/res/raw/bgm_museum_drive.mp3) · [bgm_museum_explore](../app/src/main/res/raw/bgm_museum_explore.mp3) |
| ⑤ 광교호수공원 | [bgm_lake_drive](../app/src/main/res/raw/bgm_lake_drive.mp3) · [bgm_lake_arrival](../app/src/main/res/raw/bgm_lake_arrival.mp3) · [bgm_lake_explore](../app/src/main/res/raw/bgm_lake_explore.mp3) |
| 진상 · 엔딩 | [bgm_truth](../app/src/main/res/raw/bgm_truth.mp3) · [bgm_yeoni_theme](../app/src/main/res/raw/bgm_yeoni_theme.mp3) · [bgm_ending_true](../app/src/main/res/raw/bgm_ending_true.mp3) · [bgm_ending_normal](../app/src/main/res/raw/bgm_ending_normal.mp3) · [bgm_ending_bad](../app/src/main/res/raw/bgm_ending_bad.mp3) |
| 기본 주행 BGM | [bgm_drive_siyeok.wav](../app/src/main/res/raw/bgm_drive_siyeok.wav): 장소별 곡이 없을 때 사용. [make_drive_bgm.py](../tools/make_drive_bgm.py)로 직접 합성 |

## 🔤 폰트 (SIL Open Font License)

| 폰트 | 파일 | 라이선스 |
| --- | --- | --- |
| 나눔명조 | [Bold](../app/src/main/res/font/nanum_myeongjo_bold.ttf) · [ExtraBold](../app/src/main/res/font/nanum_myeongjo_extrabold.ttf) | [OFL](licenses/NanumMyeongjo-OFL.txt) |
| Noto Serif KR | [Regular](../app/src/main/res/font/noto_serif_kr_regular.ttf) · [Bold](../app/src/main/res/font/noto_serif_kr_bold.ttf) | [OFL](licenses/NotoSerifKR-OFL.txt) |
| Cormorant SC | [Medium](../app/src/main/res/font/cormorant_sc_medium.ttf) · [Bold](../app/src/main/res/font/cormorant_sc_bold.ttf) | [OFL](licenses/CormorantSC-OFL.txt) |
| Playfair Display | [Italic](../app/src/main/res/font/playfair_display_italic.ttf) | [OFL](licenses/PlayfairDisplay-OFL.txt) |
| Playfair Display SC | [Bold](../app/src/main/res/font/playfair_display_sc_bold.ttf) | [OFL](licenses/PlayfairDisplaySC-OFL.txt) |
| Pinyon Script | [Regular](../app/src/main/res/font/pinyon_script.ttf) | [OFL](licenses/PinyonScript-OFL.txt) |

## 🖼️ 그림

모든 그림은 [app/src/main/res/drawable-nodpi](../app/src/main/res/drawable-nodpi)에 있습니다. 그림을 누르면 원본 파일이 열립니다.

**인물**

| [<img src="../app/src/main/res/drawable-nodpi/portrait_physician.webp" width="110" />](../app/src/main/res/drawable-nodpi/portrait_physician.webp) | [<img src="../app/src/main/res/drawable-nodpi/portrait_yeoni.webp" width="110" />](../app/src/main/res/drawable-nodpi/portrait_yeoni.webp) | [<img src="../app/src/main/res/drawable-nodpi/portrait_hancheol.webp" width="110" />](../app/src/main/res/drawable-nodpi/portrait_hancheol.webp) | [<img src="../app/src/main/res/drawable-nodpi/portrait_park.webp" width="110" />](../app/src/main/res/drawable-nodpi/portrait_park.webp) | [<img src="../app/src/main/res/drawable-nodpi/portrait_kim.webp" width="110" />](../app/src/main/res/drawable-nodpi/portrait_kim.webp) | [<img src="../app/src/main/res/drawable-nodpi/portrait_curator.webp" width="110" />](../app/src/main/res/drawable-nodpi/portrait_curator.webp) | [<img src="../app/src/main/res/drawable-nodpi/portrait_agency.webp" width="110" />](../app/src/main/res/drawable-nodpi/portrait_agency.webp) |
| :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| 서도윤 · 내의원 의원 | 연이 · 행궁 궁녀 | 한철 · 장용영 병사 | 박 서방 · 수문지기 | 김씨 · 둔전 노인 | 학예사 · 2026년 | 시간 조사국 |

**장면 배경**

| [<img src="../app/src/main/res/drawable-nodpi/scene_agency.webp" width="180" />](../app/src/main/res/drawable-nodpi/scene_agency.webp) | [<img src="../app/src/main/res/drawable-nodpi/scene_time_rift.webp" width="180" />](../app/src/main/res/drawable-nodpi/scene_time_rift.webp) | [<img src="../app/src/main/res/drawable-nodpi/scene_palace_dusk.webp" width="180" />](../app/src/main/res/drawable-nodpi/scene_palace_dusk.webp) |
| :---: | :---: | :---: |
| 조사국 관제실 | 시간 이동 | 화성행궁 · 해 질 녘 |
| [<img src="../app/src/main/res/drawable-nodpi/scene_palace_night.webp" width="180" />](../app/src/main/res/drawable-nodpi/scene_palace_night.webp) | [<img src="../app/src/main/res/drawable-nodpi/scene_sluice_night.webp" width="180" />](../app/src/main/res/drawable-nodpi/scene_sluice_night.webp) | [<img src="../app/src/main/res/drawable-nodpi/scene_reservoir_dawn.webp" width="180" />](../app/src/main/res/drawable-nodpi/scene_reservoir_dawn.webp) |
| 화성행궁 · 밤 | 화홍문 · 밤 | 만석거 · 새벽 |
| [<img src="../app/src/main/res/drawable-nodpi/scene_museum.webp" width="180" />](../app/src/main/res/drawable-nodpi/scene_museum.webp) | [<img src="../app/src/main/res/drawable-nodpi/scene_lake_night.webp" width="180" />](../app/src/main/res/drawable-nodpi/scene_lake_night.webp) | [<img src="../app/src/main/res/drawable-nodpi/scene_lake_modern.webp" width="180" />](../app/src/main/res/drawable-nodpi/scene_lake_modern.webp) |
| 박물관 수장고 · 2026년 | 아랫방죽 · 1795년 | 원천호수 · 2026년 |

**소품 (22개)**

| [<img src="../app/src/main/res/drawable-nodpi/item_bowl.webp" width="90" />](../app/src/main/res/drawable-nodpi/item_bowl.webp) | [<img src="../app/src/main/res/drawable-nodpi/item_notebook.webp" width="90" />](../app/src/main/res/drawable-nodpi/item_notebook.webp) | [<img src="../app/src/main/res/drawable-nodpi/item_sign.webp" width="90" />](../app/src/main/res/drawable-nodpi/item_sign.webp) | [<img src="../app/src/main/res/drawable-nodpi/item_secret_letter.webp" width="90" />](../app/src/main/res/drawable-nodpi/item_secret_letter.webp) | [<img src="../app/src/main/res/drawable-nodpi/item_paper_scrap.webp" width="90" />](../app/src/main/res/drawable-nodpi/item_paper_scrap.webp) | [<img src="../app/src/main/res/drawable-nodpi/item_bojagi.webp" width="90" />](../app/src/main/res/drawable-nodpi/item_bojagi.webp) |
| :---: | :---: | :---: | :---: | :---: | :---: |
| bowl | notebook | sign | secret_letter | paper_scrap | bojagi |
| [<img src="../app/src/main/res/drawable-nodpi/item_dial_box.webp" width="90" />](../app/src/main/res/drawable-nodpi/item_dial_box.webp) | [<img src="../app/src/main/res/drawable-nodpi/item_key_eul.webp" width="90" />](../app/src/main/res/drawable-nodpi/item_key_eul.webp) | [<img src="../app/src/main/res/drawable-nodpi/item_gate_wheel.webp" width="90" />](../app/src/main/res/drawable-nodpi/item_gate_wheel.webp) | [<img src="../app/src/main/res/drawable-nodpi/item_strong_water.webp" width="90" />](../app/src/main/res/drawable-nodpi/item_strong_water.webp) | [<img src="../app/src/main/res/drawable-nodpi/item_stele.webp" width="90" />](../app/src/main/res/drawable-nodpi/item_stele.webp) | [<img src="../app/src/main/res/drawable-nodpi/item_grain.webp" width="90" />](../app/src/main/res/drawable-nodpi/item_grain.webp) |
| dial_box | key_eul | gate_wheel | strong_water | stele | grain |
| [<img src="../app/src/main/res/drawable-nodpi/item_chest.webp" width="90" />](../app/src/main/res/drawable-nodpi/item_chest.webp) | [<img src="../app/src/main/res/drawable-nodpi/item_beacon_box.webp" width="90" />](../app/src/main/res/drawable-nodpi/item_beacon_box.webp) | [<img src="../app/src/main/res/drawable-nodpi/item_flint.png" width="90" />](../app/src/main/res/drawable-nodpi/item_flint.png) | [<img src="../app/src/main/res/drawable-nodpi/item_licorice.webp" width="90" />](../app/src/main/res/drawable-nodpi/item_licorice.webp) | [<img src="../app/src/main/res/drawable-nodpi/item_pot.png" width="90" />](../app/src/main/res/drawable-nodpi/item_pot.png) | [<img src="../app/src/main/res/drawable-nodpi/item_recipe_first.webp" width="90" />](../app/src/main/res/drawable-nodpi/item_recipe_first.webp) |
| chest | beacon_box | flint | licorice | pot | recipe_first |
| [<img src="../app/src/main/res/drawable-nodpi/item_bottles.webp" width="90" />](../app/src/main/res/drawable-nodpi/item_bottles.webp) | [<img src="../app/src/main/res/drawable-nodpi/item_vaccine.webp" width="90" />](../app/src/main/res/drawable-nodpi/item_vaccine.webp) | [<img src="../app/src/main/res/drawable-nodpi/item_news.webp" width="90" />](../app/src/main/res/drawable-nodpi/item_news.webp) | [<img src="../app/src/main/res/drawable-nodpi/item_shadow_figure.webp" width="90" />](../app/src/main/res/drawable-nodpi/item_shadow_figure.webp) | | |
| bottles | vaccine | news | shadow_figure | | |

**자물쇠 · 배경 · 로고**

| [<img src="../app/src/main/res/drawable-nodpi/num_lock.png" width="90" />](../app/src/main/res/drawable-nodpi/num_lock.png) | [<img src="../app/src/main/res/drawable-nodpi/direction_lock.png" width="90" />](../app/src/main/res/drawable-nodpi/direction_lock.png) | [<img src="../app/src/main/res/drawable-nodpi/color_lock.png" width="90" />](../app/src/main/res/drawable-nodpi/color_lock.png) | [<img src="../app/src/main/res/drawable-nodpi/english_lock.png" width="90" />](../app/src/main/res/drawable-nodpi/english_lock.png) | [<img src="../app/src/main/res/drawable-nodpi/logo_escape_road.png" width="140" />](../app/src/main/res/drawable-nodpi/logo_escape_road.png) |
| :---: | :---: | :---: | :---: | :---: |
| 숫자 자물쇠 | 방향 자물쇠 | 색상 자물쇠 | 영어 자물쇠 | 로고 |

배경 질감: [bg_velvet.jpg](../app/src/main/res/drawable-nodpi/bg_velvet.jpg) · [bg_parchment.jpg](../app/src/main/res/drawable-nodpi/bg_parchment.jpg) · 그림 제작 기준: [인물·장면](art/portraits.md) · [소품](art/props.md)
