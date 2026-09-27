# RSVP Reader Android

RSVP Reader는 일반적인 초고속 RSVP를 그대로 복제하는 앱이 아니라, 한 번에 한 토큰씩 읽도록 하면서도 현재 문장과 앞뒤 문맥을 항상 함께 보여주는 Android 독서 도구입니다. 낯설거나 어려운 단어를 무의식적으로 건너뛰지 않고 일정한 속도로 읽는 데 초점을 둡니다.

## 주요 기능

- Kotlin + Jetpack Compose 네이티브 Android 앱
- 서재: 붙여넣기 텍스트, TXT, PDF, EPUB 저장
- 문서별 제목, 진행률, 이름 변경, 삭제, 중복 텍스트 감지
- 각 문서의 정확한 마지막 RSVP 토큰 위치 자동 저장/복원
- 상단 문맥 영역: 이전 2문장 + 현재 문장 + 다음 2문장, 현재 문장/현재 토큰 동기 하이라이트
- 하단 RSVP 영역: 한 번에 한 토큰, 이전/재생·일시정지/다음
- 가운데 grapheme 기반 집중 글자를 화면의 고정 X축 중심에 맞추는 RSVP 렌더링
- 특수문자 기반 분리 규칙: `정치ㆍ경제` → `정치ㆍ` / `경제`, `state-of-the-art` → `state-` / `of-` / `the-` / `art`
- 소수와 천 단위 숫자, URL, 이메일은 가능한 한 하나의 의미 단위로 유지
- 속도 조절: 60–1200 WPM, 슬라이더/±10/직접 입력
- 선택 가능한 문장부호 지연 및 긴 단어 추가 시간
- 오른쪽에서 들어오는 전체 글 패널, LazyColumn 가상화, 현재 문장 자동 이동/하이라이트
- 집중색 표시, 집중색 선택, 문맥/RSVP 글자 크기, 다크 모드 설정
- Android Storage Access Framework를 사용하며 광범위한 저장소 권한을 요구하지 않음

## 파일 형식

- TXT: UTF-8 및 UTF-16 BOM 기반 텍스트 지원
- EPUB: EPUB ZIP 내부의 `container.xml`/OPF manifest/spine을 읽어 원래 spine 순서대로 XHTML 본문 추출
- PDF: 텍스트 레이어가 존재해 PDFBox로 문자를 추출할 수 있는 PDF 지원
- 스캔 이미지 전용 PDF OCR: 현재 버전에서 의도적으로 제외. 텍스트가 비어 있으면 사용자에게 미지원 상태를 명확히 알림

가져온 문서는 추출된 정규화 텍스트를 앱 내부 저장소에 별도 보관하므로, 원본 파일이 나중에 이동되거나 사라져도 서재의 문서를 계속 읽을 수 있습니다.

## 위치 동기화 설계

읽기 위치의 단일 source of truth는 `globalTokenIndex`입니다. 각 RSVP 토큰에는 원문 범위, 문장 인덱스, 문장 안 토큰 인덱스가 함께 저장되며 상단 문맥, RSVP, 전체 글 패널, 복원 위치가 모두 같은 토큰 모델을 사용합니다. UI별로 별도의 읽기 위치를 유지하지 않습니다.

## 빌드

요구 사항:

- JDK 17 이상
- Android SDK Platform 35 / Build Tools 35.0.0
- Gradle 8.11.1

```bash
gradle :app:assembleDebug
```

생성 APK:

`app/build/outputs/apk/debug/app-debug.apk`

GitHub Actions의 `.github/workflows/android-debug-apk.yml`에서도 `assembleDebug` 후 `RSVP-Reader-debug-apk` artifact를 업로드하도록 구성되어 있습니다.

## 의도적으로 제외한 기능

OCR, 클라우드 동기화, 메모, TTS, 밑줄/주석 등은 첫 버전 핵심 읽기 경험과 위치 동기화를 우선하기 위해 포함하지 않았습니다.
