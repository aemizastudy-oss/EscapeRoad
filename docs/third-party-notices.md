# 포함 글꼴

화면디자인 PDF의 실제 글꼴은 합성 이미지에 포함되어 있어 추출할 수 없습니다. Pleos의 시스템 `serif`가 고딕으로 표시되는 것을 확인해, 명조 표현을 일관되게 제공하는 **나눔명조 Regular** 원본 파일을 앱에 포함했습니다. PDF 원본 글꼴과 동일하다고 주장하지 않습니다.

- 저작권: Copyright (c) 2010, NHN Corporation. Reserved Font Names는 라이선스 원문 참조.
- 라이선스: SIL Open Font License 1.1. 원본 파일을 수정하지 않았습니다.
- 원본: [Google Fonts의 Nanum Myeongjo](https://github.com/google/fonts/tree/main/ofl/nanummyeongjo)
- 글꼴 파일: `app/src/main/res/font/nanum_myeongjo.ttf` (3,058,408 bytes)
- SHA-256: `7ed9e8653a8ed04285d51dc343ffea6eb3d9c73afc27383ea8929ee4ffd03205`
- 앱에 포함한 고지 전문: [OFL-NanumMyeongjo.txt](../app/src/main/assets/licenses/OFL-NanumMyeongjo.txt)

앱 실행 시 글꼴 다운로드나 네트워크 접근은 필요하지 않습니다.

## 디자인 캔버스 글꼴 (2026-11-14 추가)

claude.ai 디자인 캔버스 「Escape Road 화면 디자인」에 지정된 글꼴을 Google Fonts 저장소(`google/fonts`의 `ofl/`)에서 받아 포함했습니다. 모두 SIL Open Font License 1.1이며 고지 전문은 `app/src/main/assets/licenses/OFL-*.txt`에 있습니다. 이전의 Android cursive 대체는 Pinyon Script로 바꿨습니다.

| 파일 | 용도 | 크기(bytes) | 가공 | SHA-256 |
| --- | --- | --- | --- | --- |
| `nanum_myeongjo_bold.ttf` | 제목 700 | 3,074,720 | 없음 | `bc9ed8e6…fe3f3` |
| `nanum_myeongjo_extrabold.ttf` | 제목 800 | 3,180,888 | 없음 | `60c0077f…46db7` |
| `noto_serif_kr_regular.ttf` | 본문 400 | 1,483,156 | 가변 글꼴을 400으로 고정하고 KS X 1001 한글 2,350자·앱 문구 글자만 남긴 부분집합 | `c00deb8d…494b3` |
| `noto_serif_kr_bold.ttf` | 본문 700 | 1,485,876 | 위와 같음(700) | `f517eff8…9d3db` |
| `pinyon_script.ttf` | 영문 필기체 제목 | 151,412 | 없음 | `4aab130a…1ac29a` |
| `cormorant_sc_medium.ttf` / `cormorant_sc_bold.ttf` | 영문 소제목 | 762,320 / 763,124 | 없음 | `7ec02e6b…f89bf` / `78f70c79…19177` |
| `playfair_display_sc_bold.ttf` | 사건 카드 라벨 | 204,568 | 없음 | `b58a968c…e7915` |
| `playfair_display_italic.ttf` | 사건 게시판 부제 | 278,688 | 없음(가변 글꼴 원본, 굵기는 앱에서 지정) | `a5e26dc5…1378b` |

Noto Serif KR은 예약 글꼴 이름이 없어 부분집합으로 줄여 쓸 수 있습니다. Playfair Display는 예약 이름이 있어 원본을 수정하지 않았습니다. 앱 문구에 새 한글이 추가되면 부분집합에 없는 글자는 시스템 글꼴로 표시될 수 있으므로, 문구를 크게 바꾸면 부분집합을 다시 만들어야 합니다.

## 디자인 캔버스 질감

캔버스에 업로드된 원본 이미지를 받아 바탕·양피지 질감으로 씁니다(PDF에서 잘라낸 `pdf_wallpaper`/`pdf_paper` 대신). 다마스크 무늬는 캔버스의 SVG 경로를 코드로 그립니다.

| 파일 | 크기(bytes) | SHA-256 |
| --- | --- | --- |
| `drawable-nodpi/canvas_velvet.jpg` | 122,555 | `de5c849e…ef616` |
| `drawable-nodpi/canvas_parchment.jpg` | 24,979 | `656d76f7…96db1f` |

## 사용자 제공 PDF의 그림

2026-11-13 사용자 요청에 따라 `docs/Escape Road 화면 디자인.pdf`의 합성 JPEG에서 배경·로고·장식 영역을 잘라 PNG로 사용합니다. 새로 그리거나 외부 이미지로 대체하지 않았습니다. 일부 이미지의 배경은 색상·밝기로 투명 처리했으며, 독립된 원본 레이어를 복구한 것은 아닙니다. 원본 PDF와 사용자가 추가한 로고 파일은 보존합니다.

추출 페이지·좌표·처리 방식·해시는 [PNG 추출 명세](pdf-assets.json), 재현 코드는 [추출 스크립트](../tools/extract_pdf_assets.py)에 있습니다.
