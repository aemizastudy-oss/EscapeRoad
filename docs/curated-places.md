# 직접 준비한 장소 자료

확인일: 2026-11-15. 네이버 검색 응답을 재사용하지 않고 공식 안내에서 장소명·분류·주소만 확인해 40개 고정 카드를 수동 구성했다(기존 8개 보존, 32개 추가). 웹 문단·사진·리뷰·지도 타일을 복제하지 않았다. 아래 링크는 자료 출처이며 앱 실행 중 외부 API를 요청하지 않는다. 공공기관이 안내한다는 이유만으로 모든 저작물에 무제한 사용권이 있다고 주장하지 않는다.

| 관광지 | 카드 | 주소 | 공식 출처 |
| --- | --- | --- | --- |
| 화성행궁 | 화성행궁 주차장 | 수원시 팔달구 행궁로 18 | [수원시](https://www.suwon.go.kr/web/board/BD_board.view.do?bbsCd=1042&seq=20251008222237044) |
| 화홍문 | 화홍문 공영주차장 | 수원시 장안구 팔달로 280 | [수원도시공사](https://www.suwonudc.co.kr/park/PageLink.do?link=forward%3A%2FPageContent.do&menuNo=010000&subMenuNo=010600&tempParam1=&thirdMenuNo=010602) |
| 만석공원 | 만석공원 제5주차장 | 수원시 장안구 송정로 9 | [수원도시공사](https://www.suwonudc.co.kr/park/PageLink.do?link=forward%3A%2FPageContent.do&menuNo=010000&subMenuNo=010600&tempParam1=&thirdMenuNo=010602) |
| 수원광교박물관 | 수원광교박물관 주차장 | 수원시 영통구 광교로 182 | [박물관 FAQ](https://ggmuseum.suwon.go.kr/comm/faq.do) |
| 광교호수공원 | 원천호수 주차장 | 수원시 영통구 광교호수로 75 | [수원도시공사](https://www.suwonudc.co.kr/park/PageLink.do?link=forward%3A%2FPageContent.do&menuNo=010000&subMenuNo=010600&tempParam1=&thirdMenuNo=010602) |
| 화성행궁 | 진미통닭 | 수원시 팔달구 정조로800번길 21, 2층 | [수원시 2026년 목록](https://www.suwon.go.kr/webcontent/ckeditor/2026/1/20/d6b6f241-0f13-4757-9be2-642fa14dd722.pdf), [수원시 메뉴 소개](https://news.suwon.go.kr/?mode=blog&p=46&page=3&reqIdx=202506090448477761&viewMode=view) |
| 화홍문 | 연포갈비 | 수원시 팔달구 정조로906번길 56-1 | [한국관광공사](https://korean.visitkorea.or.kr/detail/rem_detail.do?cotid=71bb331c-0d15-4601-8ffa-ed4b1fad0df5) |
| 만석공원 | 시루향기 | 수원시 장안구 만석로 198 | [장안구 음식점 안내](https://jangan.suwon.go.kr/sub_view.asp?menu_id=sub04020302) |

개별 장소의 현재 영업·빈자리·입구 좌표는 검증하지 않았다. 따라서 장소 좌표는 null로 유지하고 길 안내는 `주변 관광지로 안내`로 표시한다. 기존 GuideStops의 관광지 대표점까지 안내를 요청하며 입구 안내나 도착 성공을 보장하지 않는다. 자료를 추가할 때는 사실 확인일·출처·안내 대상 좌표의 근거를 함께 갱신한다.


## 2026-11-15 추가한 카드

분류별 고유 카드 수는 주차 9·맛집 10·카페 6·체험 8·숙소 3·야경/저녁 4로 총 40개다. 같은 장소를 여러 관광지 권역에서 함께 추천할 수 있으며, 공유 후보는 같은 `catalogId`를 사용해 저장 중복을 막는다. 카페거리·산책로·시설 안의 카페와 체험 공간은 각각의 이용 후보 카드이므로 서로 다른 실제 업소 40곳을 뜻하지 않는다.

| 연결 관광지 | 분류 | 추가 카드 | 주소 | 공식 출처 |
| --- | --- | --- | --- | --- |
| 화성행궁 | 주차 | 남수동 공영주차장 | 경기도 수원시 팔달구 창룡대로26번길 19 | [수원문화재단 주차 안내](https://www.visitsuwon.or.kr/base/contents/view?contentsNo=15&menuLevel=3&menuNo=68) |
| 화홍문 | 주차 | 연무동 공영주차장 | 경기도 수원시 장안구 경수대로743번길 57 | [수원문화재단 주차 안내](https://www.visitsuwon.or.kr/base/contents/view?contentsNo=15&menuLevel=3&menuNo=68) |
| 광교호수공원 | 주차 | 광교 행복한들 공영주차장(제2주차장) | 경기도 수원시 영통구 광교호수로 165 | [광교호수공원 주차 안내](https://www.gglakepark.or.kr/theme/sample28/html/d4.php) |
| 광교호수공원 | 주차 | 신대호수 공영주차장(제3주차장) | 경기도 수원시 영통구 광교호수로 214 | [광교호수공원 주차 안내](https://www.gglakepark.or.kr/theme/sample28/html/d4.php) |
| 화성행궁·화홍문·만석공원 | 숙소 | 도노호텔 | 경기도 수원시 팔달구 신풍로 68 | [수원문화재단 숙소 안내](https://www.swcf.or.kr/ebook/01/sub5_1.html) |
| 수원광교박물관·광교호수공원 | 숙소 | 코트야드 바이 메리어트 수원 | 경기도 수원시 영통구 광교호수공원로 320 | [호텔 공식 홈페이지](https://www.marriott.com/ko/hotels/selcw-courtyard-suwon/overview/) |
| 화성행궁·화홍문·만석공원 | 숙소 | 노보텔 앰배서더 수원 | 경기도 수원시 팔달구 덕영대로 902 | [호텔 공식 홈페이지](https://www.ambatel.com/novotel/suwon/ko/main.do) |
| 만석공원 | 체험 | 수원시립만석전시관 | 경기도 수원시 장안구 송정로 19 | [수원시 공식 관광 안내](https://www.suwon.go.kr/sw-www/sw-visitsuwon/sw-visitsuwon-01/sw-visitsuwon-01-05/sw-visitsuwon-01-05-02.jsp) |
| 만석공원 | 체험 | 일월수목원 | 경기도 수원시 장안구 일월로 61 | [일월수목원 공식 홈페이지](https://www.suwon.go.kr/iwarbor/) |
| 수원광교박물관 | 체험 | 수원광교박물관 어린이체험실 | 경기도 수원시 영통구 광교로 182 | [수원시 공식 관광 안내](https://www.suwon.go.kr/sw-www/sw-visitsuwon/sw-visitsuwon-01/sw-visitsuwon-01-04/sw-visitsuwon-01-04-03.jsp) |
| 수원광교박물관 | 체험 | 수원박물관 | 경기도 수원시 영통구 창룡대로 265 | [수원시 공식 관광 안내](https://www.suwon.go.kr/sw-www/sw-visitsuwon/sw-visitsuwon-01/sw-visitsuwon-01-04/sw-visitsuwon-01-04-01.jsp) |
| 만석공원 | 야경·저녁 | 만석공원 저녁 산책 | 경기도 수원시 장안구 정자동 286-6 일원 | [수원시 공원 안내](https://www.suwon.go.kr/sw-www/sw-suwonpark/sw-suwonpark-02/sw-suwonpark-02-03/sw-suwonpark-02-03-01-01.jsp) |
| 수원광교박물관 | 카페 | 광교카페거리 | 경기도 수원시 영통구 이의동 1321-14 일원 | [수원시 영통구 공식 안내](https://yt.suwon.go.kr/_bbsplus/view.asp?bd_gubn=1&code=tbl_bbs_sub0209&mnuflag=&no=MTA0MTUg&page=471) |
| 수원광교박물관 | 야경·저녁 | 여천변 카페거리 저녁 산책 | 경기도 수원시 영통구 이의동 1321-14 일원 | [수원시 영통구 공식 안내](https://yt.suwon.go.kr/_bbsplus/view.asp?bd_gubn=1&code=tbl_bbs_sub0209&mnuflag=&no=MTA0MTUg&page=471) |
| 화성행궁·화홍문 | 맛집 | 용성통닭 | 경기도 수원시 팔달구 정조로800번길 15 | [수원시 팔달구 음식점 안내](https://paldal.suwon.go.kr/submain_view.asp?TopID=sub20&menuid=sub20020301) |
| 화성행궁·화홍문 | 맛집 | 남문통닭 본점 | 경기도 수원시 팔달구 정조로800번길 16 | [남문통닭 공식 홈페이지·수원시 업소표](https://suwonnammuntongdak.com/home) |
| 화성행궁·화홍문 | 카페 | 정지영커피로스터즈 행궁본점 | 경기도 수원시 팔달구 신풍로 42 | [한국관광공사 공식 관광 안내](https://korean.visitkorea.or.kr/detail/rem_detail.do?cotid=ffa6160a-c51d-4468-a3c8-62cc2c49ab3e) |
| 화성행궁·화홍문 | 카페 | 행궁동 어울림카페 | 경기도 수원시 팔달구 수원천로 351, 행궁동 어울림센터 1층 | [수원시 공식 운영 안내](https://www.suwon.go.kr/web/board/BD_board.view.do?bbsCd=1042&seq=20220429140808089) |
| 화성행궁·화홍문 | 체험 | 수원전통문화관 전통식생활체험관 | 경기도 수원시 팔달구 정조로 893 | [수원문화재단 공식 안내](https://www.swcf.or.kr/?p=155) |
| 화성행궁·화홍문 | 체험 | 연무대 국궁체험 | 경기도 수원시 팔달구 창룡대로103번길 8 | [수원문화재단 체험 안내](https://www.swcf.or.kr/?idx=75&listGubun=list&p=74&page=1&viewMode=view) |
| 화성행궁·화홍문 | 야경·저녁 | 방화수류정·용연 야경 | 경기도 수원시 팔달구 수원천로392번길 44-6 | [한국관광공사·수원문화재단](https://english.visitkorea.or.kr/svc/contents/contentsView.do?menuSn=351&vcontsId=62377) |
| 수원광교박물관·광교호수공원 | 맛집 | 335키친 광교아브뉴프랑점 | 경기도 수원시 영통구 센트럴타운로 85 | [한국관광공사 공식 관광 안내](https://access.visitkorea.or.kr/food/detail.do?cotId=a2ae6672-0087-4632-b167-764767da0d8c) |
| 수원광교박물관 | 맛집 | 신라갈비 수원본점 | 경기도 수원시 영통구 동수원로 538 | [한국관광공사 공식 관광 안내](https://korean.visitkorea.or.kr/detail/rem_detail.do?cotid=6f1616fd-1b06-4d7f-b564-16440db52a93) |
| 광교호수공원 | 맛집 | 수원 키친 | 경기도 수원시 영통구 광교호수공원로 320 | [호텔 공식 다이닝 안내](https://www.marriott.com/ko/hotels/selcw-courtyard-suwon/dining/) |
| 광교호수공원 | 카페 | 티크닉 앨리웨이 광교점 | 경기도 수원시 영통구 광교호수공원로 80, 광교아이파크 판매지동 2층 242호 | [한국관광공사 공식 관광 안내](https://access.visitkorea.or.kr/food/detail.do?cotId=ea1bee84-b9c5-4f95-8474-0842f2365c0d) |
| 광교호수공원 | 체험 | 아쿠아플라넷 광교 | 경기도 수원시 영통구 광교중앙로 124 | [한국관광공사 공식 관광 안내](https://access.visitkorea.or.kr/ms/detail.do?cotId=78e34f92-e8b9-4666-88a3-dfd21825daa5) |
| 광교호수공원 | 야경·저녁 | 광교 프라이부르크전망대 | 경기도 수원시 영통구 광교호수로 127 | [한국관광공사·수원문화재단](https://access.visitkorea.or.kr/ms/detail.do?cotId=83268cd4-7267-4a1c-b039-5670f71053d1) |
| 만석공원 | 맛집 | 장인화로 만석공원점 | 경기도 수원시 장안구 정조로 1092 | [수원시 공식 음식점 안내](https://www.suwon.go.kr/web/tour/cultureTour/cultureTourView.do?categoryCd=145&mstSeqNo=1189261) |
| 만석공원 | 맛집 | 미랑 | 경기도 수원시 장안구 만석로 181 | [수원시 공식 음식점 안내](https://www.suwon.go.kr/web/tour/cultureTour/cultureTourView.do?categoryCd=145&mstSeqNo=1110052) |
| 만석공원 | 체험 | 복합문화공간 111CM | 경기도 수원시 장안구 수성로 195 | [수원문화재단 공식 시설 안내](https://www.swcf.or.kr/?p=333) |
| 만석공원 | 카페 | 111CM 카페·휴게공간 | 경기도 수원시 장안구 수성로 195, 111CM A동 | [수원문화재단 공식 시설 안내](https://www.swcf.or.kr/?p=333) |
| 만석공원 | 카페 | 정지영커피로스터즈 스타필드수원점 | 경기도 수원시 장안구 수성로 175, 스타필드수원 2층 | [정지영커피로스터즈 공식 매장 안내](https://jungjiyoungcoffee.com/shopinfo/store.html) |

확인일은 위와 같은 2026-11-15다. 이름·주소·분류·시설 구성만 수동 전사했으며 API 검색 결과를 캐싱한 데이터가 아니다. 추가 설명은 팀의 짧은 안내 문구로 작성했다. 각 공식 페이지의 사진·리뷰·지도·소개 문단은 수록하지 않았다.

권역 연결은 주소를 기준으로 한 편집 선택이며 도로 최단거리나 도보 가능 판정이 아니다. 도노호텔·노보텔은 만석공원 인접 숙소로 표시하지 않고 차량 이동을 안내한다. 아쿠아플라넷 광교 주소는 한국관광공사 상세 안내의 광교중앙로 124를 사용한다. 광교 프라이부르크전망대 야경 근거는 [수원문화재단](https://www.swcf.or.kr/webzine/_001/kor/?bbsCate=38&idx=84043&mode=view&rIdx=0&webPage=2), 방화수류정 야경 근거는 [수원문화재단](https://www.swcf.or.kr/?p=66), 남문통닭 주소의 보조 근거는 [수원시 업소표](https://www.suwon.go.kr/webcontent/ckeditor/2026/9/1/b4ccfcba-534e-4855-b87b-35918f208466.pdf), 어울림카페 운영의 보조 근거는 [수원시 2026년 기사](https://news.suwon.go.kr/?mode=photo&p=40&page=19&reqIdx=202608071928569998&viewMode=view)다. 행사·교육·예약·매장 영업은 방문 전에 별도로 확인한다.
