package com.example.roadescape.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.roadescape.R
import com.example.roadescape.game.*
import com.example.roadescape.model.*

@Composable
fun ArrivalScreen(state:GameUiState,puzzleId:String,onOpen:()->Unit){
    val content=state.content?:return
    val session=state.session?:return
    val cp=content.checkpoints.find{it.puzzleId==puzzleId}?:return
    val gate=GameController.gate(content,session,state.environment,puzzleId)
    if(!state.canReadContent||gate!=GateReason.READY){CasePausedScreen(state,gate);return}
    BoxWithConstraints(Modifier.fillMaxSize().testTag("screen-arrival")){
        val letter:@Composable ()->Unit={PaperSheet(Modifier.fillMaxWidth()){
            Text("FRAGMENT ${content.checkpoints.indexOf(cp)+1}",letterSpacing=3.sp,color=CaseColors.Ink.copy(alpha=.65f))
            CaseArtwork(CaseArt.ENVELOPE,Modifier.fillMaxWidth().height(160.dp))
            Text("새로운 편지가 탐정님을 기다립니다.",fontFamily=CaseFonts.Serif,fontStyle=FontStyle.Italic,fontSize=28.sp,color=CaseColors.Ink)
            Text("${cp.title} · 개발용 도착 확인",color=CaseColors.Ink)
        }}
        val detail:@Composable ()->Unit={Column(verticalArrangement=Arrangement.spacedBy(22.dp)){
            Text("P · ${cp.title}",color=CaseColors.Gold)
            CaseHeading("The Seal is Broken","다음 단서를 살펴볼 시간입니다")
            Text("도착 기록과 주차 확인을 마쳤습니다. 화면의 가상 자물쇠를 풀고 수사 기록에 단서를 남겨 보세요.",style=MaterialTheme.typography.bodyLarge,color=CaseColors.MutedGold)
            Text("모은 단서 ${session.acquiredClues.size} / ${content.clues.size}",color=CaseColors.Gold)
            EscapeButton("편지 펼쳐보기","arrival-open",onOpen)
        }}
        if(maxWidth>=900.dp)Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(36.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.weight(1f).verticalScroll(rememberScrollState())){letter()};Box(Modifier.weight(1f).verticalScroll(rememberScrollState())){detail()}}
        else Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(24.dp)){letter();detail()}
    }
}

@Composable
fun CasePuzzleScreen(state:GameUiState,puzzleId:String,command:(GameCommand)->Unit,onHints:()->Unit,onAnswer:()->Unit,onSkip:()->Unit,onNext:()->Unit){
    val content=state.content?:return
    val session=state.session?:return
    val puzzle=content.puzzles.find{it.id==puzzleId}?:return
    val cp=content.checkpoints.first{it.puzzleId==puzzleId}
    val progress=session.progress.first{it.checkpointId==cp.id}
    val holder=rememberSaveableStateHolder()
    val gate=GameController.gate(content,session,state.environment,puzzleId)
    LaunchedEffect(session.sessionId,puzzleId,gate,progress.status){if(gate==GateReason.READY&&progress.status==CheckpointStatus.NOT_STARTED)command(GameCommand.StartPuzzle(session.sessionId,puzzleId))}
    if(!state.canReadContent){CasePausedScreen(state,gate);return}
    if(progress.status.isComplete){
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("screen-puzzle"),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(24.dp,Alignment.CenterVertically)){
            CaseArtwork(if(progress.status==CheckpointStatus.SOLVED)CaseArt.WAX else CaseArt.ENVELOPE,Modifier.size(140.dp))
            CaseHeading("Another Page in the Casebook",if(progress.status==CheckpointStatus.SOLVED)"봉인이 풀렸습니다"else"이 자물쇠는 건너뛰었습니다")
            Text(cp.title,color=CaseColors.Gold)
            Text(if(progress.status==CheckpointStatus.SOLVED)stringResource(R.string.feedback_correct)else stringResource(R.string.feedback_skip))
            EscapeButton(stringResource(R.string.next_checkpoint),"next-checkpoint",onNext)
        };return
    }
    if(gate!=GateReason.READY&&gate!=GateReason.BUSY){CasePausedScreen(state,gate);return}
    val enabled=gate==GateReason.READY&&!state.saving
    BoxWithConstraints(Modifier.fillMaxSize().testTag("screen-puzzle")){
        val letter:@Composable ()->Unit={Column(verticalArrangement=Arrangement.spacedBy(16.dp)){
            Text("CASE No. 01 · FRAGMENT ${content.checkpoints.indexOf(cp)+1} · ${cp.title}",letterSpacing=1.sp,color=CaseColors.Gold,style=MaterialTheme.typography.labelLarge)
            PaperSheet(Modifier.fillMaxWidth()){
                Text("Fragment ${content.checkpoints.indexOf(cp)+1} — ${puzzle.title}",color=CaseColors.Ink.copy(alpha=.65f),fontFamily=CaseFonts.Serif)
                Text("“${puzzle.prompt}”",fontSize=22.sp,lineHeight=36.sp,color=CaseColors.Ink)
                Text("— The Detective's Drive Club",modifier=Modifier.align(Alignment.End),fontFamily=FontFamily.Cursive,fontStyle=FontStyle.Italic,color=CaseColors.Ink.copy(alpha=.7f))
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.wrong_attempts,progress.wrongAttempts),color=CaseColors.Ink.copy(alpha=.65f))
            }
            PuzzleFeedback(state.feedback,state.rejectedGate)
            Text(stringResource(R.string.hint_count,progress.revealedHintCount,puzzle.hints.size),modifier=Modifier.testTag("hint-count"),color=CaseColors.MutedGold)
            puzzle.hints.take(progress.revealedHintCount).forEachIndexed{index,hint->
                CasePanel(Modifier.fillMaxWidth().testTag("hint-content")){Text("힌트 ${index+1}",color=CaseColors.Gold);Text(hint)}
            }
            if(progress.answerRevealed)PaperSheet(Modifier.fillMaxWidth().testTag("answer-content")){
                Text(stringResource(R.string.answer_title),color=CaseColors.Ink,fontWeight=FontWeight.Bold)
                Text(displayAnswer(puzzle.lock),color=CaseColors.Ink,fontSize=24.sp)
                Text(puzzle.solution,color=CaseColors.Ink)
            }
            FlowRow(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                EscapeButton(stringResource(R.string.hint_request),"open-hints",onHints,enabled,secondary=true)
                EscapeButton(stringResource(R.string.reveal_answer),"reveal-answer",onAnswer,enabled&&!progress.answerRevealed,secondary=true)
                EscapeButton(stringResource(R.string.skip),"skip",onSkip,enabled,secondary=true)
            }
        }}
        val lock:@Composable ()->Unit={holder.SaveableStateProvider("${session.sessionId}:$puzzleId"){
            CaseLockControls(puzzle,enabled){command(GameCommand.Submit(session.sessionId,puzzleId,it))}
        }}
        if(maxWidth>=900.dp)Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(32.dp)){
            Box(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom=16.dp)){letter()}
            Box(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom=16.dp)){lock()}
        }else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(24.dp)){letter();lock();Spacer(Modifier.height(16.dp))}
    }
}

@Composable
private fun CaseLockControls(puzzle:PuzzleDefinition,enabled:Boolean,onSubmit:(LockInput)->Unit){
    var digits by rememberSaveable(puzzle.id){mutableStateOf("")}
    var sequence by rememberSaveable(puzzle.id){mutableStateOf(listOf<String>())}
    var channel by rememberSaveable(puzzle.id){mutableIntStateOf(0)}
    var level by rememberSaveable(puzzle.id){mutableIntStateOf(0)}
    fun rotateDigit(index:Int,step:Int){val current=digits.padEnd(index+1,'0').toCharArray();current[index]=('0'.code+((current[index]-'0'+step+10)%10)).toChar();digits=current.concatToString()}
    val title=when(puzzle.lock){is LockDefinition.Numeric->"The Combination Lock";is LockDefinition.Sequence->"The Directional Lock";is LockDefinition.StateCombination->"The Device Lock"}
    Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)){
        Text(title,fontFamily=FontFamily.Cursive,fontStyle=FontStyle.Italic,fontSize=30.sp,color=CaseColors.Gold)
        when(val lock=puzzle.lock){
            is LockDefinition.Numeric->{
                Text(stringResource(R.string.numeric_length,lock.length),color=CaseColors.MutedGold)
                BrassLock(Modifier.fillMaxWidth()){
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp,Alignment.CenterHorizontally)){
                        repeat(lock.length){index->Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally){
                            TextButton({rotateDigit(index,1)},enabled=enabled,modifier=Modifier.heightIn(min=56.dp).fillMaxWidth().testTag("dial-up-$index"),colors=ButtonDefaults.textButtonColors(contentColor=CaseColors.Ink)){Text("▴",fontSize=26.sp)}
                            Box(Modifier.fillMaxWidth().height(60.dp).background(CaseColors.Cream).border(.7.dp,CaseColors.Ink.copy(alpha=.4f)),contentAlignment=Alignment.Center){Text(digits.getOrNull(index)?.toString()?:"?",fontSize=34.sp,fontFamily=CaseFonts.Serif,color=CaseColors.Ink)}
                            TextButton({rotateDigit(index,-1)},enabled=enabled,modifier=Modifier.heightIn(min=56.dp).fillMaxWidth().testTag("dial-down-$index"),colors=ButtonDefaults.textButtonColors(contentColor=CaseColors.Ink)){Text("▾",fontSize=26.sp)}
                        }}
                    }
                }
                Text(digits.ifEmpty{"입력 대기"},modifier=Modifier.testTag("numeric-input"),color=CaseColors.Gold,letterSpacing=8.sp)
                (listOf(1,2,3,4,5,6,7,8,9,0)).chunked(5).forEach{row->Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){row.forEach{digit->EscapeButton(digit.toString(),"digit-$digit",{if(digits.length<lock.length)digits+=digit},enabled,Modifier.weight(1f),secondary=true)}}}
            }
            is LockDefinition.Sequence->{
                BoxWithConstraints(Modifier.size(250.dp)){
                    val artwork=painterResource(R.drawable.pdf_direction_lock)
                    val aspect=artwork.intrinsicSize.width/artwork.intrinsicSize.height
                    val imageWidth=minOf(maxWidth.value,maxHeight.value*aspect).dp
                    val imageHeight=imageWidth/aspect
                    val imageLeft=(maxWidth-imageWidth)/2
                    val imageTop=(maxHeight-imageHeight)/2
                    Image(artwork,contentDescription=null,modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Fit)
                    Direction.entries.forEach{direction->
                        val center=when(direction){Direction.UP->.5f to .442f;Direction.RIGHT->.843f to .660f;Direction.DOWN->.5f to .883f;Direction.LEFT->.157f to .660f}
                        val description=stringResource(when(direction){Direction.UP->R.string.direction_up;Direction.RIGHT->R.string.direction_right;Direction.DOWN->R.string.direction_down;Direction.LEFT->R.string.direction_left})
                        // The PDF already contains each arrow; native transparent buttons provide input and accessibility.
                        TextButton({if(sequence.size<lock.answer.size)sequence=sequence+direction.name},enabled=enabled,
                            modifier=Modifier.offset(x=imageLeft+imageWidth*center.first-28.dp,y=imageTop+imageHeight*center.second-28.dp)
                                .size(56.dp).testTag("sequence-${direction.name}").semantics{contentDescription=description},
                            contentPadding=PaddingValues(0.dp),
                        ){}
                    }
                }
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    repeat(lock.answer.size){index->Box(Modifier.size(56.dp).border(.8.dp,CaseColors.Gold.copy(alpha=if(index<sequence.size)1f else .35f)),contentAlignment=Alignment.Center){Text(sequence.getOrNull(index)?.let{directionGlyph(Direction.valueOf(it))}?:"·",color=CaseColors.Gold,fontSize=26.sp)}}
                }
                Text(sequence.joinToString(" "){directionGlyph(Direction.valueOf(it))}.ifEmpty{"입력 대기"},modifier=Modifier.testTag("sequence-input"),color=CaseColors.MutedGold)
                Text("화면 방향 버튼으로 입력합니다 · 실제 차량 조작과 무관합니다",color=CaseColors.MutedGold,style=MaterialTheme.typography.bodySmall)
            }
            is LockDefinition.StateCombination->{
                Text(stringResource(R.string.virtual_device),color=CaseColors.MutedGold)
                BrassLock(Modifier.fillMaxWidth()){
                    Text("CHANNEL",color=CaseColors.Ink,letterSpacing=3.sp)
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                        (lock.min..lock.max).forEach{v->EscapeButton(v.toString(),"channel-$v",{channel=v},enabled,secondary=channel!=v)}
                    }
                    Spacer(Modifier.height(10.dp));Text("LEVEL",color=CaseColors.Ink,letterSpacing=3.sp)
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                        (lock.min..lock.max).forEach{v->EscapeButton(v.toString(),"level-$v",{level=v},enabled,secondary=level!=v)}
                    }
                }
            }
        }
        EscapeButton(stringResource(R.string.lock_pull),"puzzle-submit",{onSubmit(when(puzzle.lock){is LockDefinition.Numeric->LockInput.Numeric(digits);is LockDefinition.Sequence->LockInput.Sequence(sequence.map(Direction::valueOf));is LockDefinition.StateCombination->LockInput.StateCombination(mapOf("channel" to channel,"level" to level))})},enabled,Modifier.fillMaxWidth())
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            if(puzzle.lock !is LockDefinition.StateCombination)EscapeButton(stringResource(R.string.delete),"puzzle-delete",{digits=digits.dropLast(1);sequence=sequence.dropLast(1)},enabled,secondary=true)
            EscapeButton(stringResource(R.string.input_restart),"puzzle-clear",{digits="";sequence=emptyList();channel=0;level=0},enabled,secondary=true)
        }
    }
}

@Composable private fun BrassLock(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit){
    Column(modifier,horizontalAlignment=Alignment.CenterHorizontally){
        Image(painterResource(R.drawable.pdf_lock_shackle),contentDescription=null,modifier=Modifier.width(160.dp).height(90.dp),contentScale=ContentScale.Fit)
        Box(Modifier.fillMaxWidth()){
            Image(painterResource(R.drawable.pdf_brass_plate),contentDescription=null,modifier=Modifier.matchParentSize(),contentScale=ContentScale.FillBounds)
            Column(Modifier.fillMaxWidth().border(1.dp,CaseColors.Ink.copy(alpha=.55f)).padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)
        }
    }
}

@Composable fun CaseHintsScreen(state:GameUiState,puzzleId:String,command:(GameCommand)->Unit,onAnswer:()->Unit,onBack:()->Unit){
    val content=state.content?:return;val session=state.session?:return
    val puzzle=content.puzzles.find{it.id==puzzleId}?:return
    val cp=content.checkpoints.first{it.puzzleId==puzzleId};val progress=session.progress.first{it.checkpointId==cp.id}
    val gate=GameController.gate(content,session,state.environment,puzzleId)
    if(!state.canReadContent||(gate!=GateReason.READY&&gate!=GateReason.BUSY)){CasePausedScreen(state,gate);return}
    val enabled=gate==GateReason.READY&&!state.saving
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("screen-hints"),verticalArrangement=Arrangement.spacedBy(20.dp)){
        CaseHeading("A Word from the Butler",stringResource(R.string.hint_request),side="${progress.revealedHintCount} / ${puzzle.hints.size} 공개")
        Text("집사가 조심스럽게 쪽지를 건넵니다. 공개한 힌트와 정답 열람은 수사 기록에 남습니다.",color=CaseColors.MutedGold)
        BoxWithConstraints(Modifier.fillMaxWidth()){
            @Composable fun Card(index:Int,modifier:Modifier){
                val revealed=index<progress.revealedHintCount
                val next=index==progress.revealedHintCount
                CasePanel(modifier,selected=next){
                    Text(if(index==0)"Tier I"else"Tier II",color=CaseColors.Gold,letterSpacing=2.sp)
                    Text(if(index==0)"작은 실마리"else"결정적 단서",style=MaterialTheme.typography.titleLarge)
                    Text(if(revealed)puzzle.hints[index]else"봉인된 쪽지를 열면 다음 실마리를 확인할 수 있습니다.",modifier=Modifier.heightIn(min=86.dp))
                    EscapeButton(if(revealed)"확인한 힌트"else if(next)"열어보기"else"봉인됨",if(next)"hint-next"else"hint-step-${index+1}",{
                        command(GameCommand.RevealHint(session.sessionId,puzzleId,index+1))
                    },enabled&&next,modifier=Modifier.fillMaxWidth(),secondary=!next)
                }
            }
            @Composable fun Answer(modifier:Modifier){CasePanel(modifier){
                Text("The Solution",color=CaseColors.Gold,letterSpacing=2.sp)
                Text("정답 공개",style=MaterialTheme.typography.titleLarge)
                Text(if(progress.answerRevealed)puzzle.solution else"정답과 짧은 해설을 확인합니다. 정답을 보아도 직접 제출하거나 건너뛰어야 합니다.",modifier=Modifier.heightIn(min=86.dp))
                EscapeButton(if(progress.answerRevealed) "공개함" else stringResource(R.string.reveal_answer),"reveal-answer",onAnswer,enabled&&!progress.answerRevealed,Modifier.fillMaxWidth(),secondary=true)
            }}
            if(maxWidth>=900.dp)Row(horizontalArrangement=Arrangement.spacedBy(16.dp)){Card(0,Modifier.weight(1f));Card(1,Modifier.weight(1f));Answer(Modifier.weight(1f))}
            else Column(verticalArrangement=Arrangement.spacedBy(16.dp)){Card(0,Modifier.fillMaxWidth());Card(1,Modifier.fillMaxWidth());Answer(Modifier.fillMaxWidth())}
        }
        EscapeButton(stringResource(R.string.return_puzzle),"hints-back",onBack,modifier=Modifier.align(Alignment.End),secondary=true)
    }
}

@Composable private fun PuzzleFeedback(feedback:GameFeedback?,gate:GateReason?){
    val text=when(feedback){GameFeedback.INVALID_INPUT->R.string.feedback_invalid;GameFeedback.INCORRECT->R.string.feedback_incorrect;GameFeedback.ARRIVED->R.string.feedback_arrived;GameFeedback.BLOCKED->gate?.let(::gateLabel);else->null}
    if(text!=null)Text(stringResource(text),color=CaseColors.Gold,modifier=Modifier.testTag("puzzle-feedback"))
}
private fun directionGlyph(value:Direction)=when(value){Direction.UP->"↑";Direction.RIGHT->"→";Direction.DOWN->"↓";Direction.LEFT->"←"}
internal fun displayAnswer(lock:LockDefinition):String=when(lock){is LockDefinition.Numeric->lock.answer;is LockDefinition.Sequence->lock.answer.joinToString(" "){directionGlyph(it)};is LockDefinition.StateCombination->"채널 ${lock.channel} · 레벨 ${lock.level}"}
