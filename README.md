# RSVP Reader Android

RSVP Reader는 일반적인 초고속 RSVP를 그대로 복제하는 앱이 아니라, 한 번에 한 토큰씩 읽도록 하면서도 현재 문장과 앞뒤 문맥을 항상 함께 보여주는 Android 독서 도구입니다. 낯설거나 어려운 단어를 무의식적으로 건너뛰지 않고 일정한 속도로 읽는 데 초점을 둡니다.

## 주요 기능

- Kotlin + Jetpack Compose 네이티브 Android 앱
- 서재: 붙여넣기 텍스트, TXT, PDF, EPUB 저장
- 문서별 제목, 진행률, 이름 변경, 삭제, 중복 텍스트 감지
- 각 문서의 정확한 마지막 RSVP 토큰 위치 자동 저장/복원
- 문맥 영역: 현재 문장을 항상 중심에 두고, 공간이 허용되면 앞뒤 최대 2문장까지 표시하며 현재 문장/현재 토큰을 동기 하이라이트
- 하단 RSVP 영역: 한 번에 한 토큰, 이전/재생·일시정지/다음
- 가운데 grapheme 기반 집중 글자를 화면의 고정 X축 중심에 맞추는 RSVP 렌더링
- 특수문자 기반 분리 규칙: `정치ㆍ경제` → `정치ㆍ` / `경제`, `state-of-the-art` → `state-` / `of-` / `the-` / `art`
- 소수와 천 단위 숫자, URL, 이메일은 가능한 한 하나의 의미 단위로 유지
- 속도 조절: 60–600 WPM, 슬라이더/±10/직접 입력
- 선택 가능한 문장부호 지연 및 긴 단어 추가 시간
- 오른쪽에서 들어오는 전체 글 패널, LazyColumn 가상화, 현재 문장 자동 이동/하이라이트
- 집중색 표시/선택, 문맥/RSVP 글자 크기, 밝은 배경색 선택, 다크 모드 설정
- 시스템 TTS 엔진/음성 선택, TTS 배속 및 가능한 엔진에서 단어 범위 기반 RSVP 동기화
- Android Storage Access Framework를 사용하며 광범위한 저장소 권한을 요구하지 않음

## 파일 형식

- TXT: UTF-8 우선, UTF-16 BOM 및 잘못된 UTF-8인 경우 CP949/EUC-KR 계열 fallback 지원
- EPUB: EPUB ZIP 내부의 `container.xml`/OPF manifest/spine을 읽어 원래 spine 순서대로 XHTML 본문 추출
- PDF: 텍스트 레이어가 존재해 PDFBox로 문자를 추출할 수 있는 PDF 지원
- 스캔 이미지 전용 PDF OCR: 현재 버전에서 의도적으로 제외. 텍스트가 비어 있으면 사용자에게 미지원 상태를 명확히 알림

가져온 문서는 추출된 정규화 텍스트를 앱 내부 저장소에 별도 보관하므로, 원본 파일이 나중에 이동되거나 사라져도 서재의 문서를 계속 읽을 수 있습니다.

## 위치 동기화 설계

읽기 위치의 단일 source of truth는 `globalTokenIndex`입니다. 각 RSVP 토큰에는 원문 범위, 문장 인덱스, 문장 안 토큰 인덱스가 함께 저장되며 문맥, RSVP, 전체 글 패널, 복원 위치가 모두 같은 토큰 모델을 사용합니다. UI별로 별도의 읽기 위치를 유지하지 않습니다. 재생 중 위치는 메모리에서 즉시 갱신하고 영구 저장은 짧은 간격으로 묶어서 수행하며, 화면 이탈과 lifecycle 종료 시점에는 최신 위치를 즉시 동기 저장합니다.

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

GitHub Actions의 `.github/workflows/bootstrap-build.yml`에서 JVM 단위 테스트와 `assembleDebug`를 실행한 뒤 `RSVP-Reader-debug-apk` artifact를 업로드합니다.

## 의도적으로 제외한 기능

OCR, 클라우드 동기화, 메모, 밑줄/주석 등은 현재 핵심 읽기 경험과 위치 동기화를 우선하기 위해 포함하지 않았습니다.


## Update signing

Starting with version 1.0.1, debug APKs are signed with the repository's dedicated development update key so future debug APKs can be installed as updates over this version. This key is for development builds only and must not be reused for production distribution.

The earlier 1.0.0 artifact was signed by an ephemeral GitHub-hosted runner debug key. Its private key no longer exists, so Android cannot accept 1.0.1 as an in-place update over that exact APK. If 1.0.0 is already installed, uninstall it once before installing 1.0.1. Builds after 1.0.1 retain the same signing identity.
