package com.example.roadescape.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.compose.*
import com.example.roadescape.BuildConfig
import com.example.roadescape.R
import com.example.roadescape.game.*
import com.example.roadescape.model.CheckpointStatus
import com.example.roadescape.model.TourMode

private enum class ConfirmType { NEW_GAME, NEW_TRIP, ANSWER, SKIP }
private data class PendingConfirm(val type:ConfirmType,val sessionId:String="",val puzzleId:String="",val mode:TourMode=TourMode.MOCK)

@Composable
fun RoadEscapeApp(vm:GameViewModel,state:GameUiState,onPermissions:()->Unit={}) {
    val nav=rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route=entry?.destination?.route?:"start"
    val legacyPreview=state.copy(content=state.legacyPreview?:state.content)
    var confirm by remember {mutableStateOf<PendingConfirm?>(null)}
    var menu by remember {mutableStateOf(false)}
    var wrong by remember {mutableStateOf(false)}
    var shownWrong by remember {mutableStateOf("")}
    fun go(destination:String){nav.navigate(destination){launchSingleTop=true}}
    val home:()->Unit={nav.navigate("start"){popUpTo("start"){inclusive=true};launchSingleTop=true}}
    val back:()->Unit={if(!nav.popBackStack())home()}
    val begin:()->Unit={if(state.session==null&&state.error==null)vm.newGameConfirmed("demo_package",TourMode.MOCK)else confirm=PendingConfirm(ConfirmType.NEW_GAME)}
    fun answer(id:String){confirm=PendingConfirm(ConfirmType.ANSWER,state.session?.sessionId.orEmpty(),id)}
    fun skip(id:String){confirm=PendingConfirm(ConfirmType.SKIP,state.session?.sessionId.orEmpty(),id)}
    LaunchedEffect(vm){vm.navigation.collect{nav.navigate(it){popUpTo("start");launchSingleTop=true}}}
    LaunchedEffect(state.canReadContent,state.session?.sessionId,state.tourGate){
        if(!state.canReadContent||(state.content?.requiresTour==true&&state.tourGate!=TourGate.READY)){
            if(confirm?.type==ConfirmType.ANSWER||confirm?.type==ConfirmType.SKIP)confirm=null;wrong=false
        }
    }
    LaunchedEffect(state.feedback,state.session,route){
        val key="${state.session?.sessionId}:${state.session?.progress?.sumOf{it.wrongAttempts}}"
        if(state.feedback==GameFeedback.INCORRECT&&route=="puzzle/{puzzleId}"&&state.canReadContent&&key!=shownWrong){shownWrong=key;wrong=true}
    }
    Surface(Modifier.fillMaxSize(),color=CaseColors.DeepBurgundy){Box(Modifier.safeDrawingPadding()){
        CaseFrame(Modifier.fillMaxSize(),framed=route!="start"){
            Column(Modifier.fillMaxSize().padding(horizontal=26.dp,vertical=18.dp)){
                CaseNavigationHeader(state,route,home,back,{go("checkpoints")},{go("clues")},{menu=true})
                EnvironmentPanel(vm,state)
                if(state.saving){LinearProgressIndicator(Modifier.fillMaxWidth().testTag("saving"),color=CaseColors.Gold);Text(stringResource(R.string.saving),style=MaterialTheme.typography.labelSmall)}
                Box(Modifier.weight(1f).fillMaxWidth().padding(top=12.dp)){
                    val ready=!state.loading&&state.error==null
                    NavHost(nav,startDestination="start"){
                        composable("start"){if(ready)LobbyScreen(state,{go("cases")},{go("archive")},{go("casebook")},{go("checkpoints")},{go("tour-setup")},{go("sdk-status")})}
                        composable("tour-setup"){if(ready)TourSetupScreen(state,{mode->
                            if(state.session==null)vm.startSuwonTour(mode)else confirm=PendingConfirm(ConfirmType.NEW_TRIP,mode=mode)
                        },onPermissions,back)}
                        composable("sdk-status"){SdkStatusScreen(vm,state,onPermissions,back)}
                        composable("cases"){if(ready)CaseBoardScreen(legacyPreview,back,{go("case-detail")})}
                        composable("case-detail"){if(ready)CaseDetailScreen(legacyPreview,back,{go("rules")})}
                        composable("rules"){if(ready)DetectiveRulesScreen(back,{go("party")})}
                        composable("party"){if(ready)PartyScreen(back,{go("prologue")})}
                        composable("prologue"){if(ready)PrologueScreen(legacyPreview,back,begin)}
                        composable("checkpoints"){if(ready)Column(Modifier.fillMaxSize()){
                            if(state.content?.requiresTour==true)Box(Modifier.fillMaxWidth().heightIn(max=360.dp).verticalScroll(rememberScrollState())){TourProgressPanel(vm,state)}
                            Box(Modifier.weight(1f)){CaseCheckpointScreen(state,{id->
                            val session=state.session;val content=state.content
                            val checkpoint=content?.checkpoints?.find{it.puzzleId==id}
                            val progress=session?.progress?.find{it.checkpointId==checkpoint?.id}
                            val gate=if(session!=null&&content!=null)GameController.gate(content,session,state.environment,id)else null
                            go(if(gate==GateReason.READY&&progress?.status==CheckpointStatus.NOT_STARTED)"arrival/$id" else "puzzle/$id")
                        },{go("completion")})}}}
                        composable("arrival/{puzzleId}"){p->if(ready)ArrivalScreen(state,p.arguments?.getString("puzzleId").orEmpty()){go("puzzle/${p.arguments?.getString("puzzleId")}")}}
                        composable("puzzle/{puzzleId}"){p->if(ready){val id=p.arguments?.getString("puzzleId").orEmpty();Column(Modifier.fillMaxSize()){
                            if(state.mode==TourMode.LIVE&&state.canReadContent)TourVoiceControls(vm,state)
                            Box(Modifier.weight(1f)){CasePuzzleScreen(state,id,vm::command,{go("hints/$id")},{answer(id)},{skip(id)},{go("checkpoints")})}
                        }}}
                        composable("hints/{puzzleId}"){p->if(ready){val id=p.arguments?.getString("puzzleId").orEmpty();CaseHintsScreen(state,id,vm::command,{answer(id)},back)}}
                        composable("clues"){if(ready)SatchelScreen(state){go("clue/$it")}}
                        composable("clue/{clueId}"){p->if(ready)ClueDetailScreen(state,p.arguments?.getString("clueId").orEmpty(),back)}
                        composable("archive"){if(ready)CaseArchiveScreen(state,{go("checkpoints")},{go("cases")})}
                        composable("notebook"){if(ready)CaseNotebookScreen(state,back)}
                        composable("save"){if(ready)CaseSaveScreen(state,home,back,{go("checkpoints")})}
                        composable("completion"){if(ready)CaseCompletionScreen(state,{go("cases")},{go("solution")},{go("certificate")},{go("rating")})}
                        composable("solution"){if(ready)CaseSolutionScreen(state,home,{go("rating")})}
                        composable("certificate"){if(ready)CaseCertificateScreen(state,home)}
                        composable("rating"){if(ready)CaseRatingScreen(state,home)}
                        composable("casebook"){if(ready)CasebookScreen(state,{go("checkpoints")},{go("solution")},{go("certificate")})}
                        composable("locks"){if(ready)LockCollectionScreen(state,back)}
                    }
                    when{state.loading->CaseLoadingScreen();state.error!=null->CaseErrorScreen(state.error,vm::retry,begin)}
                }
            }
        }
    }}
    if(menu)CaseDialog("The Detective's Desk","탐정의 책상",{menu=false}){
        listOf("tour-setup" to "수원 관광 코스","sdk-status" to "SDK 연결 상태","notebook" to "수첩","archive" to "저장된 수사","casebook" to "사건 기록부","locks" to "자물쇠 도감","save" to "저장 확인","rules" to "탐정 수칙").chunked(2).forEach{row->
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){row.forEach{(path,label)->EscapeButton(label,"menu-$path",{menu=false;go(path)},enabled=state.session!=null||path !in setOf("notebook","save"),modifier=Modifier.weight(1f),secondary=true)}}
        }
        EscapeButton(stringResource(R.string.close),"menu-close",{menu=false},modifier=Modifier.align(Alignment.End))
    }
    confirm?.let{pending->
        val title=when(pending.type){ConfirmType.NEW_GAME,ConfirmType.NEW_TRIP->R.string.confirm_new_title;ConfirmType.ANSWER->R.string.confirm_answer_title;ConfirmType.SKIP->R.string.confirm_skip_title}
        val body=when(pending.type){ConfirmType.NEW_GAME,ConfirmType.NEW_TRIP->R.string.confirm_new_body;ConfirmType.ANSWER->R.string.confirm_answer_body;ConfirmType.SKIP->R.string.confirm_skip_body}
        CaseDialog(if(pending.type==ConfirmType.SKIP)"Leave This Lock Unsolved?"else"A Note Before You Continue",stringResource(title),{confirm=null}){
            Text(stringResource(body),color=CaseColors.MutedGold,style=MaterialTheme.typography.bodyLarge)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                EscapeButton(stringResource(R.string.cancel),"confirmation-cancel",{confirm=null},secondary=true)
                EscapeButton(stringResource(R.string.confirm),"confirmation-confirm",{confirm=null;when(pending.type){
                    ConfirmType.NEW_GAME->vm.newGameConfirmed("demo_package",TourMode.MOCK)
                    ConfirmType.NEW_TRIP->vm.startSuwonTour(pending.mode)
                    ConfirmType.ANSWER->vm.command(GameCommand.RevealAnswer(pending.sessionId,pending.puzzleId))
                    ConfirmType.SKIP->vm.command(GameCommand.Skip(pending.sessionId,pending.puzzleId))
                }})
            }
        }
    }
    if(wrong&&state.canReadContent)CaseDialog("It won't budge…","자물쇠가 꿈쩍도 하지 않습니다",{wrong=false}){
        CaseArtwork(CaseArt.LOCK,Modifier.align(Alignment.CenterHorizontally).size(110.dp))
        Text(stringResource(R.string.feedback_incorrect),color=CaseColors.MutedGold)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            EscapeButton(stringResource(R.string.hint_request),"wrong-hint",{wrong=false;entry?.arguments?.getString("puzzleId")?.let{go("hints/$it")}},secondary=true)
            EscapeButton("다시 시도","wrong-retry",{wrong=false})
        }
    }
}

@Composable private fun CaseNavigationHeader(state:GameUiState,route:String,onHome:()->Unit,onBack:()->Unit,onMap:()->Unit,onClues:()->Unit,onMenu:()->Unit){
    BoxWithConstraints(Modifier.fillMaxWidth().padding(bottom=8.dp)){
        val compact=maxWidth<850.dp
        Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                if(route!="start"){EscapeButton("‹","nav-back",onBack,secondary=true);EscapeButton(stringResource(R.string.nav_lobby),"nav-home",onHome,secondary=true)}
                if(!compact||route=="start")Column(Modifier.weight(1f)){
                    Text("ESCAPE ROAD",fontFamily=CaseFonts.Serif,fontSize=16.sp,letterSpacing=3.sp,color=CaseColors.Gold)
                    Text(inputModeBadge(state),fontSize=11.sp,color=CaseColors.MutedGold)
                }else Spacer(Modifier.weight(1f))
                if(!compact){if(state.session!=null){EscapeButton(stringResource(R.string.nav_case_route),"tab-checkpoints",onMap,secondary=true);EscapeButton(stringResource(R.string.nav_satchel),"tab-clues",onClues,secondary=true)};EscapeButton(stringResource(R.string.nav_more),"open-menu",onMenu,secondary=true)}
                Text(stringResource(parkingLabel(state.parking)),color=CaseColors.Gold,style=MaterialTheme.typography.labelMedium)
            }
            if(compact)FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                if(state.session!=null){EscapeButton(stringResource(R.string.nav_case_route),"tab-checkpoints",onMap,secondary=true);EscapeButton(stringResource(R.string.nav_satchel),"tab-clues",onClues,secondary=true)}
                EscapeButton(stringResource(R.string.nav_more),"open-menu",onMenu,secondary=true)
                if(route!="start")Text(inputModeBadge(state),fontSize=11.sp,color=CaseColors.MutedGold)
            }
            HorizontalDivider(color=CaseColors.Gold.copy(alpha=.35f))
        }
    }
}

private fun inputModeBadge(state:GameUiState)=if(state.mode==TourMode.LIVE)"Pleos 실제 SDK · 수신 상태 확인"else if(BuildConfig.DEBUG)"개발용 시뮬레이션"else"시연 저장 · 실제 SDK 코스를 시작하세요"

@Composable fun CaseDialog(english:String,title:String,onDismiss:()->Unit,content:@Composable ColumnScope.()->Unit){
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)){
        CaseFrame(Modifier.padding(24.dp).widthIn(max=800.dp).fillMaxWidth()){
            Column(Modifier.padding(32.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(22.dp)){CaseHeading(english,title);content()}
        }
    }
}

@Composable private fun CaseLoadingScreen(){
    Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(24.dp,Alignment.CenterVertically)){
        CaseArtwork(CaseArt.ENVELOPE,Modifier.size(100.dp));CircularProgressIndicator(color=CaseColors.Gold);Text(stringResource(R.string.loading))
    }
}

@Composable private fun CaseErrorScreen(error:GameUiError,onRetry:()->Unit,onReset:()->Unit){
    val description=when{error.contentError->R.string.error_package;error.reason=="CORRUPT"->R.string.error_corrupt;error.reason=="UNSUPPORTED_SCHEMA"->R.string.error_schema;error.reason=="CONTENT_MISMATCH"||error.reason=="INVALID_PROGRESS"->R.string.error_content;else->R.string.error_io}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag(if(error.contentError)"startup-error"else"storage-error"),verticalArrangement=Arrangement.spacedBy(20.dp)){
        CaseHeading("A Letter We Couldn't Read",stringResource(if(error.contentError)R.string.content_error else R.string.storage_error))
        PaperSheet(Modifier.fillMaxWidth()){Text(stringResource(description),color=CaseColors.Ink,style=MaterialTheme.typography.titleMedium)}
        FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){EscapeButton(stringResource(R.string.retry),"retry",onRetry);if(!error.contentError)EscapeButton(stringResource(R.string.new_game),"error-reset",onReset,secondary=true)}
    }
}

@Composable private fun CaseSaveScreen(state:GameUiState,onHome:()->Unit,onBack:()->Unit,onMap:()->Unit){
    if(!state.canReadContent){CasePausedScreen(state);return}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("screen-save"),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(22.dp,Alignment.CenterVertically)){
        CaseArtwork(CaseArt.CLOCK,Modifier.size(120.dp));Text("The Case is Saved",fontFamily=CaseFonts.Script,fontStyle=FontStyle.Italic,fontSize=32.sp,color=CaseColors.Gold)
        Text("수사 기록을 저장했습니다",style=MaterialTheme.typography.headlineMedium)
        Text("${state.session?.progress?.count{it.status.isComplete}?:0} / ${state.content?.checkpoints?.size?:0} 경유지 완료",color=CaseColors.Gold,style=MaterialTheme.typography.headlineSmall)
        Text("마지막으로 저장을 마친 진행에서 언제든 이어갈 수 있습니다. 제한 시간은 없습니다.",color=CaseColors.MutedGold)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){EscapeButton(stringResource(R.string.nav_lobby_return),"save-home",onHome);EscapeButton(stringResource(R.string.nav_case_route),"save-map",onMap,secondary=true);EscapeButton("수사 재개","save-resume",onBack,secondary=true)}
    }
}
