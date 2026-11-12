# 포함 글꼴

화면디자인 PDF의 실제 글꼴은 합성 이미지에 포함되어 있어 추출할 수 없습니다. Pleos의 시스템 `serif`가 고딕으로 표시되는 것을 확인해, 명조 표현을 일관되게 제공하는 **나눔명조 Regular** 원본 파일을 앱에 포함했습니다. PDF 원본 글꼴과 동일하다고 주장하지 않습니다.

- 저작권: Copyright (c) 2010, NHN Corporation. Reserved Font Names는 라이선스 원문 참조.
- 라이선스: SIL Open Font License 1.1. 원본 파일을 수정하지 않았습니다.
- 원본: [Google Fonts의 Nanum Myeongjo](https://github.com/google/fonts/tree/main/ofl/nanummyeongjo)
- 글꼴 파일: `app/src/main/res/font/nanum_myeongjo.ttf` (3,058,408 bytes)
- SHA-256: `7ed9e8653a8ed04285d51dc343ffea6eb3d9c73afc27383ea8929ee4ffd03205`
- 앱에 포함한 고지 전문: [OFL-NanumMyeongjo.txt](../app/src/main/assets/licenses/OFL-NanumMyeongjo.txt)

앱 실행 시 글꼴 다운로드나 네트워크 접근은 필요하지 않습니다. 영문 장식 제목은 Android의 cursive 서체를 사용합니다.

## 사용자 제공 PDF의 그림

2026-11-13 사용자 요청에 따라 `docs/Escape Road 화면 디자인.pdf`의 합성 JPEG에서 배경·로고·장식 영역을 잘라 PNG로 사용합니다. 새로 그리거나 외부 이미지로 대체하지 않았습니다. 일부 이미지의 배경은 색상·밝기로 투명 처리했으며, 독립된 원본 레이어를 복구한 것은 아닙니다. 원본 PDF와 사용자가 추가한 로고 파일은 보존합니다.

추출 페이지·좌표·처리 방식·해시는 [PNG 추출 명세](pdf-assets.json), 재현 코드는 [추출 스크립트](../tools/extract_pdf_assets.py)에 있습니다.
