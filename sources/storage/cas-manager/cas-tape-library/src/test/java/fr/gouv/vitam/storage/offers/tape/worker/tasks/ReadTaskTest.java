package fr.gouv.vitam.storage.offers.tape.worker.tasks;

import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.common.storage.tapelibrary.TapeDriveConf;
import fr.gouv.vitam.storage.engine.common.model.QueueState;
import fr.gouv.vitam.storage.engine.common.model.ReadOrder;
import fr.gouv.vitam.storage.engine.common.model.TapeCatalog;
import fr.gouv.vitam.storage.engine.common.model.TapeLocation;
import fr.gouv.vitam.storage.engine.common.model.TapeLocationType;
import fr.gouv.vitam.storage.offers.tape.dto.TapeResponse;
import fr.gouv.vitam.storage.offers.tape.exception.QueueException;
import fr.gouv.vitam.storage.offers.tape.exception.TapeCatalogException;
import fr.gouv.vitam.storage.offers.tape.spec.TapeCatalogService;
import fr.gouv.vitam.storage.offers.tape.spec.TapeDriveCommandService;
import fr.gouv.vitam.storage.offers.tape.spec.TapeDriveService;
import fr.gouv.vitam.storage.offers.tape.spec.TapeLoadUnloadService;
import fr.gouv.vitam.storage.offers.tape.spec.TapeReadWriteService;
import fr.gouv.vitam.storage.offers.tape.spec.TapeRobotPool;
import fr.gouv.vitam.storage.offers.tape.spec.TapeRobotService;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

//FIXME "Should be fixed after merge"
@Ignore("Should be fixed after merge")
public class ReadTaskTest {
    public static final String FAKE_LIBRARY = "fakeLibrary";
    public static final String FAKE_TAPE_CODE = "fakeTapeCode";
    public static final Integer SLOT_INDEX = 1;
    public static final Integer DRIVE_INDEX = 0;
    private static final Integer FILE_POSITION = 0;
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private TapeRobotPool tapeRobotPool;

    @Mock
    private TapeDriveService tapeDriveService;

    @Mock
    private TapeCatalogService tapeCatalogService;

    @Mock
    private TapeReadWriteService tapeReadWriteService;

    @Before
    public void setUp() {

        reset(tapeRobotPool);
        reset(tapeDriveService);
        reset(tapeCatalogService);
        reset(tapeReadWriteService);

        when(tapeDriveService.getReadWriteService(eq(TapeDriveService.ReadWriteCmd.DD)))
            .thenAnswer(o -> tapeReadWriteService);
    }

    @Test
    public void testConstructor() {
        // When
        when(tapeDriveService.getTapeDriveConf()).thenAnswer(o -> mock(TapeDriveConf.class));

        // Test constructors
        try {
            new ReadTask(null, null, null, null, null);
            fail("should throw exception");
        } catch (IllegalArgumentException e) {
        }

        try {
            new ReadTask(mock(ReadOrder.class), null, null, null, null);
            fail("should throw exception");
        } catch (IllegalArgumentException e) {
        }

        try {
            new ReadTask(null, null, tapeRobotPool, null, null);
            fail("should throw exception");
        } catch (IllegalArgumentException e) {
        }

        try {
            new ReadTask(null, null, null, tapeDriveService, null);
            fail("should throw exception");
        } catch (IllegalArgumentException e) {
        }

        try {
            new ReadTask(null, null, null, null, tapeCatalogService);
            fail("should throw exception");
        } catch (IllegalArgumentException e) {
        }

        try {
            new ReadTask(mock(ReadOrder.class), null, tapeRobotPool, tapeDriveService, tapeCatalogService);
        } catch (IllegalArgumentException e) {
            fail("should not throw exception");
        }
    }

    @Test
    public void testReadTaskCurrentTapeIsNotNullAndEligible() {
        // When
        when(tapeDriveService.getTapeDriveConf()).thenAnswer(o -> mock(TapeDriveConf.class));
        TapeCatalog tapeCatalog = new TapeCatalog()
                .setLibrary(FAKE_LIBRARY)
                .setCode(FAKE_TAPE_CODE)
                .setCurrentPosition(FILE_POSITION)
                .setCurrentLocation(new TapeLocation(DRIVE_INDEX, TapeLocationType.DRIVE));

        ReadOrder readOrder = new ReadOrder().setTapeCode(FAKE_TAPE_CODE)
                .setFilePosition(FILE_POSITION)
                .setFileName("file_test.tar");

        ReadTask readTask =
            new ReadTask(readOrder, tapeCatalog, tapeRobotPool, tapeDriveService, tapeCatalogService);

        when(tapeReadWriteService.readFromTape(readOrder.getFileName()))
                .thenReturn(new TapeResponse(StatusCode.OK));
        when(tapeDriveService.getDriveCommandService())
                .thenReturn(mock(TapeDriveCommandService.class));
        when(tapeDriveService.getDriveCommandService().rewind())
                .thenReturn(new TapeResponse(StatusCode.OK));
        when(tapeDriveService.getDriveCommandService().goToPosition(anyInt(), anyBoolean()))
                .thenReturn(new TapeResponse(StatusCode.OK));
        when(tapeDriveService.getDriveCommandService().goToPosition(anyInt()))
                .thenReturn(new TapeResponse(StatusCode.OK));

        // Case one current t
        ReadWriteResult result = readTask.get();

        assertThat(result).isNotNull();
        assertThat(result.getOrderState()).isEqualTo(QueueState.COMPLETED);
        assertThat(result.getCurrentTape()).isNotNull();
        assertThat(result.getCurrentTape().getCode()).isEqualTo(FAKE_TAPE_CODE);
        assertThat(result.getCurrentTape().getCurrentLocation()
                .equals(new TapeLocation(DRIVE_INDEX, TapeLocationType.DRIVE))).isEqualTo(true);
        assertThat(result.getCurrentTape().getCurrentPosition()).isEqualTo(FILE_POSITION + 1);

    }

    @Test
    public void testReadTaskWhenCurrentTapeIsNotNullAndNotEligibleAndTheEligibleTapeIsAvailable() throws InterruptedException, QueueException {
        // When
        when(tapeDriveService.getTapeDriveConf()).thenAnswer(o -> mock(TapeDriveConf.class));
        TapeCatalog currentTape = new TapeCatalog()
                .setLibrary(FAKE_LIBRARY)
                .setCode("tape")
                .setCurrentLocation(new TapeLocation(DRIVE_INDEX, TapeLocationType.DRIVE))
                .setPreviousLocation(new TapeLocation(SLOT_INDEX + 1, TapeLocationType.SLOT));

        TapeCatalog appropriateTape = new TapeCatalog()
                .setLibrary(FAKE_LIBRARY)
                .setCode(FAKE_TAPE_CODE)
                .setCurrentLocation(new TapeLocation(SLOT_INDEX, TapeLocationType.SLOT));

        ReadOrder readOrder = new ReadOrder().setTapeCode(FAKE_TAPE_CODE)
                .setFilePosition(FILE_POSITION)
                .setFileName("file_test.tar");

        ReadTask readTask =
                new ReadTask(readOrder, currentTape, tapeRobotPool, tapeDriveService, tapeCatalogService);

        when(tapeReadWriteService.readFromTape(readOrder.getFileName()))
                .thenReturn(new TapeResponse(StatusCode.OK));
        when(tapeDriveService.getDriveCommandService())
                .thenReturn(mock(TapeDriveCommandService.class));
        when(tapeDriveService.getDriveCommandService().rewind())
                .thenReturn(new TapeResponse(StatusCode.OK));
        when(tapeDriveService.getDriveCommandService().goToPosition(anyInt(), anyBoolean()))
                .thenReturn(new TapeResponse(StatusCode.OK));
        when(tapeDriveService.getDriveCommandService().goToPosition(anyInt()))
                .thenReturn(new TapeResponse(StatusCode.OK));
        when(tapeCatalogService.receive(any(), any()))
                .thenReturn(Optional.of(appropriateTape));
        when(tapeDriveService.getTapeDriveConf().getIndex())
                .thenReturn(DRIVE_INDEX);
        when(tapeRobotPool.checkoutRobotService())
                .thenReturn(mock(TapeRobotService.class));
        when(tapeRobotPool.checkoutRobotService().getLoadUnloadService())
                .thenReturn(mock(TapeLoadUnloadService.class));
        when(tapeRobotPool.checkoutRobotService().getLoadUnloadService()
                .loadTape(anyInt(), anyInt())).thenReturn(new TapeResponse(StatusCode.OK));
        when(tapeRobotPool.checkoutRobotService().getLoadUnloadService()
                .unloadTape(anyInt(), anyInt())).thenReturn(new TapeResponse(StatusCode.OK));

        // Case one current t
        ReadWriteResult result = readTask.get();

        assertThat(result).isNotNull();
        assertThat(result.getOrderState()).isEqualTo(QueueState.COMPLETED);
        assertThat(result.getCurrentTape()).isNotNull();
        assertThat(result.getCurrentTape().getCode()).isEqualTo(FAKE_TAPE_CODE);
        assertThat(result.getCurrentTape().getCurrentLocation().equals(new TapeLocation(DRIVE_INDEX, TapeLocationType.DRIVE))).isEqualTo(true);
        assertThat(currentTape.getCurrentLocation().equals(new TapeLocation(SLOT_INDEX + 1, TapeLocationType.SLOT))).isEqualTo(true);
        assertThat(result.getCurrentTape().getCurrentPosition()).isEqualTo(FILE_POSITION + 1);
    }

    @Test
    public void testReadTaskWhenCurrentTapeIsNotNullAndNotEligibleAndTheEligibleTapeIsBusy() throws InterruptedException, QueueException, TapeCatalogException {
        // When
        when(tapeDriveService.getTapeDriveConf()).thenAnswer(o -> mock(TapeDriveConf.class));
        TapeCatalog currentTape = new TapeCatalog()
                .setLibrary(FAKE_LIBRARY)
                .setCode("tape")
                .setCurrentLocation(new TapeLocation(DRIVE_INDEX, TapeLocationType.DRIVE))
                .setPreviousLocation(new TapeLocation(SLOT_INDEX + 1, TapeLocationType.SLOT));

        TapeCatalog appropriateTape = new TapeCatalog()
                .setLibrary(FAKE_LIBRARY)
                .setCode(FAKE_TAPE_CODE)
                .setCurrentLocation(new TapeLocation(SLOT_INDEX, TapeLocationType.SLOT));

        ReadOrder readOrder = new ReadOrder().setTapeCode(FAKE_TAPE_CODE)
                .setFilePosition(FILE_POSITION)
                .setFileName("file_test.tar");

        ReadTask readTask =
                new ReadTask(readOrder, currentTape, tapeRobotPool, tapeDriveService, tapeCatalogService);

        when(tapeReadWriteService.readFromTape(readOrder.getFileName()))
                .thenReturn(new TapeResponse(StatusCode.OK));
        when(tapeDriveService.getDriveCommandService())
                .thenReturn(mock(TapeDriveCommandService.class));
        when(tapeDriveService.getDriveCommandService().rewind())
                .thenReturn(new TapeResponse(StatusCode.OK));
        when(tapeDriveService.getDriveCommandService().goToPosition(anyInt(), anyBoolean()))
                .thenReturn(new TapeResponse(StatusCode.OK));
        when(tapeDriveService.getDriveCommandService().goToPosition(anyInt()))
                .thenReturn(new TapeResponse(StatusCode.OK));
        when(tapeCatalogService.receive(any(), any()))
                .thenReturn(Optional.empty());
        when(tapeCatalogService.find(any()))
                .thenReturn(Arrays.asList(appropriateTape));
        when(tapeDriveService.getTapeDriveConf().getIndex())
                .thenReturn(DRIVE_INDEX);
        when(tapeRobotPool.checkoutRobotService())
                .thenReturn(mock(TapeRobotService.class));
        when(tapeRobotPool.checkoutRobotService().getLoadUnloadService())
                .thenReturn(mock(TapeLoadUnloadService.class));
        when(tapeRobotPool.checkoutRobotService().getLoadUnloadService()
                .loadTape(anyInt(), anyInt())).thenReturn(new TapeResponse(StatusCode.OK));
        when(tapeRobotPool.checkoutRobotService().getLoadUnloadService()
                .unloadTape(anyInt(), anyInt())).thenReturn(new TapeResponse(StatusCode.OK));

        // Case one current t
        ReadWriteResult result = readTask.get();

        assertThat(result).isNotNull();
        assertThat(result.getOrderState()).isEqualTo(QueueState.READY);
        assertThat(result.getCurrentTape()).isNull();
    }

    @Test
    public void testReadTaskWhenCurrentTapeIsNotNullAndNotEligibleAndTheEligibleTapeIsUnknown() throws InterruptedException, QueueException, TapeCatalogException {
        // When
        when(tapeDriveService.getTapeDriveConf()).thenAnswer(o -> mock(TapeDriveConf.class));
        TapeCatalog currentTape = new TapeCatalog()
                .setLibrary(FAKE_LIBRARY)
                .setCode("tape")
                .setCurrentLocation(new TapeLocation(DRIVE_INDEX, TapeLocationType.DRIVE))
                .setPreviousLocation(new TapeLocation(SLOT_INDEX + 1, TapeLocationType.SLOT));

        ReadOrder readOrder = new ReadOrder().setTapeCode(FAKE_TAPE_CODE)
                .setFilePosition(FILE_POSITION)
                .setFileName("file_test.tar");

        ReadTask readTask =
                new ReadTask(readOrder, currentTape, tapeRobotPool, tapeDriveService, tapeCatalogService);

        when(tapeReadWriteService.readFromTape(readOrder.getFileName()))
                .thenReturn(new TapeResponse(StatusCode.OK));
        when(tapeDriveService.getDriveCommandService())
                .thenReturn(mock(TapeDriveCommandService.class));
        when(tapeDriveService.getDriveCommandService().rewind())
                .thenReturn(new TapeResponse(StatusCode.OK));
        when(tapeDriveService.getDriveCommandService().goToPosition(anyInt(), anyBoolean()))
                .thenReturn(new TapeResponse(StatusCode.OK));
        when(tapeDriveService.getDriveCommandService().goToPosition(anyInt()))
                .thenReturn(new TapeResponse(StatusCode.OK));
        when(tapeCatalogService.receive(any(), any()))
                .thenReturn(Optional.empty());
        when(tapeCatalogService.find(any()))
                .thenReturn(Collections.emptyList());
        when(tapeDriveService.getTapeDriveConf().getIndex())
                .thenReturn(DRIVE_INDEX);
        when(tapeRobotPool.checkoutRobotService())
                .thenReturn(mock(TapeRobotService.class));
        when(tapeRobotPool.checkoutRobotService().getLoadUnloadService())
                .thenReturn(mock(TapeLoadUnloadService.class));
        when(tapeRobotPool.checkoutRobotService().getLoadUnloadService()
                .loadTape(anyInt(), anyInt())).thenReturn(new TapeResponse(StatusCode.OK));
        when(tapeRobotPool.checkoutRobotService().getLoadUnloadService()
                .unloadTape(anyInt(), anyInt())).thenReturn(new TapeResponse(StatusCode.OK));

        // Case one current t
        ReadWriteResult result = readTask.get();

        assertThat(result).isNotNull();
        assertThat(result.getOrderState()).isEqualTo(QueueState.ERROR);
        assertThat(result.getCurrentTape()).isNull();
    }

    @Test
    public void testReadTaskWhenCurrentTapeIsNotNullAndNotEligibleAndTapeCatalogThrownException() throws InterruptedException, QueueException {
        // When
        when(tapeDriveService.getTapeDriveConf()).thenAnswer(o -> mock(TapeDriveConf.class));
        TapeCatalog currentTape = new TapeCatalog()
                .setLibrary(FAKE_LIBRARY)
                .setCode("tape")
                .setCurrentLocation(new TapeLocation(DRIVE_INDEX, TapeLocationType.DRIVE))
                .setPreviousLocation(new TapeLocation(SLOT_INDEX + 1, TapeLocationType.SLOT));

        ReadOrder readOrder = new ReadOrder().setTapeCode(FAKE_TAPE_CODE)
                .setFilePosition(FILE_POSITION)
                .setFileName("file_test.tar");

        ReadTask readTask =
                new ReadTask(readOrder, currentTape, tapeRobotPool, tapeDriveService, tapeCatalogService);

        when(tapeReadWriteService.readFromTape(readOrder.getFileName()))
                .thenReturn(new TapeResponse(StatusCode.OK));
        when(tapeDriveService.getDriveCommandService())
                .thenReturn(mock(TapeDriveCommandService.class));
        when(tapeDriveService.getDriveCommandService().rewind())
                .thenReturn(new TapeResponse(StatusCode.OK));
        when(tapeDriveService.getDriveCommandService().goToPosition(anyInt(), anyBoolean()))
                .thenReturn(new TapeResponse(StatusCode.OK));
        when(tapeDriveService.getDriveCommandService().goToPosition(anyInt()))
                .thenReturn(new TapeResponse(StatusCode.OK));
        when(tapeCatalogService.receive(any(), any()))
                .thenThrow(QueueException.class);
        when(tapeDriveService.getTapeDriveConf().getIndex())
                .thenReturn(DRIVE_INDEX);
        when(tapeRobotPool.checkoutRobotService())
                .thenReturn(mock(TapeRobotService.class));
        when(tapeRobotPool.checkoutRobotService().getLoadUnloadService())
                .thenReturn(mock(TapeLoadUnloadService.class));
        when(tapeRobotPool.checkoutRobotService().getLoadUnloadService()
                .loadTape(anyInt(), anyInt())).thenReturn(new TapeResponse(StatusCode.OK));
        when(tapeRobotPool.checkoutRobotService().getLoadUnloadService()
                .unloadTape(anyInt(), anyInt())).thenReturn(new TapeResponse(StatusCode.OK));

        // Case one current t
        ReadWriteResult result = readTask.get();

        assertThat(result).isNotNull();
        assertThat(result.getOrderState()).isEqualTo(QueueState.READY);
        assertThat(result.getCurrentTape()).isNull();
    }

    @Test
    public void testReadTaskWhenCurrentTapeIsNotNullAndNotEligibleAndTheEligibleTapeIsOutside() throws InterruptedException, QueueException {
        // When
        when(tapeDriveService.getTapeDriveConf()).thenAnswer(o -> mock(TapeDriveConf.class));
        TapeCatalog currentTape = new TapeCatalog()
                .setLibrary(FAKE_LIBRARY)
                .setCode("tape")
                .setCurrentLocation(new TapeLocation(DRIVE_INDEX, TapeLocationType.DRIVE))
                .setPreviousLocation(new TapeLocation(SLOT_INDEX + 1, TapeLocationType.SLOT));

        TapeCatalog appropriateTape = new TapeCatalog()
                .setLibrary(FAKE_LIBRARY)
                .setCode(FAKE_TAPE_CODE)
                .setCurrentLocation(new TapeLocation(null, TapeLocationType.OUTSIDE));

        ReadOrder readOrder = new ReadOrder().setTapeCode(FAKE_TAPE_CODE)
                .setFilePosition(FILE_POSITION)
                .setFileName("file_test.tar");

        ReadTask readTask =
                new ReadTask(readOrder, currentTape, tapeRobotPool, tapeDriveService, tapeCatalogService);

        when(tapeReadWriteService.readFromTape(readOrder.getFileName()))
                .thenReturn(new TapeResponse(StatusCode.OK));
        when(tapeDriveService.getDriveCommandService())
                .thenReturn(mock(TapeDriveCommandService.class));
        when(tapeDriveService.getDriveCommandService().rewind())
                .thenReturn(new TapeResponse(StatusCode.OK));
        when(tapeDriveService.getDriveCommandService().goToPosition(anyInt(), anyBoolean()))
                .thenReturn(new TapeResponse(StatusCode.OK));
        when(tapeDriveService.getDriveCommandService().goToPosition(anyInt()))
                .thenReturn(new TapeResponse(StatusCode.OK));
        when(tapeCatalogService.receive(any(), any()))
                .thenReturn(Optional.of(appropriateTape));
        when(tapeDriveService.getTapeDriveConf().getIndex())
                .thenReturn(DRIVE_INDEX);
        when(tapeRobotPool.checkoutRobotService())
                .thenReturn(mock(TapeRobotService.class));
        when(tapeRobotPool.checkoutRobotService().getLoadUnloadService())
                .thenReturn(mock(TapeLoadUnloadService.class));
        when(tapeRobotPool.checkoutRobotService().getLoadUnloadService()
                .loadTape(anyInt(), anyInt())).thenReturn(new TapeResponse(StatusCode.OK));
        when(tapeRobotPool.checkoutRobotService().getLoadUnloadService()
                .unloadTape(anyInt(), anyInt())).thenReturn(new TapeResponse(StatusCode.OK));

        // Case one current t
        ReadWriteResult result = readTask.get();

        assertThat(result).isNotNull();
        assertThat(result.getOrderState()).isEqualTo(QueueState.ERROR);
        assertThat(result.getCurrentTape()).isNull();
    }

    @Test
    public void testReadTaskWhenCurrentTapeIsNotNullAndNotEligibleThenFatalWhenLoadingTheEligibleTape() throws InterruptedException, QueueException {
        // When
        when(tapeDriveService.getTapeDriveConf()).thenAnswer(o -> mock(TapeDriveConf.class));
        TapeCatalog currentTape = new TapeCatalog()
                .setLibrary(FAKE_LIBRARY)
                .setCode("tape")
                .setCurrentLocation(new TapeLocation(DRIVE_INDEX, TapeLocationType.DRIVE))
                .setPreviousLocation(new TapeLocation(SLOT_INDEX + 1, TapeLocationType.SLOT));

        TapeCatalog appropriateTape = new TapeCatalog()
                .setLibrary(FAKE_LIBRARY)
                .setCode(FAKE_TAPE_CODE)
                .setCurrentLocation(new TapeLocation(SLOT_INDEX, TapeLocationType.SLOT));

        ReadOrder readOrder = new ReadOrder().setTapeCode(FAKE_TAPE_CODE)
                .setFilePosition(FILE_POSITION)
                .setFileName("file_test.tar");

        ReadTask readTask =
                new ReadTask(readOrder, currentTape, tapeRobotPool, tapeDriveService, tapeCatalogService);

        when(tapeReadWriteService.readFromTape(readOrder.getFileName()))
                .thenReturn(new TapeResponse(StatusCode.OK));
        when(tapeDriveService.getDriveCommandService())
                .thenReturn(mock(TapeDriveCommandService.class));
        when(tapeDriveService.getDriveCommandService().rewind())
                .thenReturn(new TapeResponse(StatusCode.OK));
        when(tapeDriveService.getDriveCommandService().goToPosition(anyInt(), anyBoolean()))
                .thenReturn(new TapeResponse(StatusCode.OK));
        when(tapeDriveService.getDriveCommandService().goToPosition(anyInt()))
                .thenReturn(new TapeResponse(StatusCode.OK));
        when(tapeCatalogService.receive(any(), any()))
                .thenReturn(Optional.of(appropriateTape));
        when(tapeDriveService.getTapeDriveConf().getIndex())
                .thenReturn(DRIVE_INDEX);
        when(tapeRobotPool.checkoutRobotService())
                .thenReturn(mock(TapeRobotService.class));
        when(tapeRobotPool.checkoutRobotService().getLoadUnloadService())
                .thenReturn(mock(TapeLoadUnloadService.class));
        when(tapeRobotPool.checkoutRobotService().getLoadUnloadService()
                .loadTape(anyInt(), anyInt())).thenReturn(new TapeResponse(StatusCode.FATAL));
        when(tapeRobotPool.checkoutRobotService().getLoadUnloadService()
                .unloadTape(anyInt(), anyInt())).thenReturn(new TapeResponse(StatusCode.OK));

        // Case one current t
        ReadWriteResult result = readTask.get();

        assertThat(result).isNotNull();
        assertThat(result.getOrderState()).isEqualTo(QueueState.ERROR);
        assertThat(result.getCurrentTape()).isNull();
    }

    @Test
    public void testReadTaskWhenCurrentTapeIsNotNullAndNotEligibleThenKoWhenLoadingTheEligibleTape() throws InterruptedException, QueueException {
        // When
        when(tapeDriveService.getTapeDriveConf()).thenAnswer(o -> mock(TapeDriveConf.class));
        TapeCatalog currentTape = new TapeCatalog()
                .setLibrary(FAKE_LIBRARY)
                .setCode("tape")
                .setCurrentLocation(new TapeLocation(DRIVE_INDEX, TapeLocationType.DRIVE))
                .setPreviousLocation(new TapeLocation(SLOT_INDEX + 1, TapeLocationType.SLOT));

        TapeCatalog appropriateTape = new TapeCatalog()
                .setLibrary(FAKE_LIBRARY)
                .setCode(FAKE_TAPE_CODE)
                .setCurrentLocation(new TapeLocation(SLOT_INDEX, TapeLocationType.SLOT));

        ReadOrder readOrder = new ReadOrder().setTapeCode(FAKE_TAPE_CODE)
                .setFilePosition(FILE_POSITION)
                .setFileName("file_test.tar");

        ReadTask readTask =
                new ReadTask(readOrder, currentTape, tapeRobotPool, tapeDriveService, tapeCatalogService);

        when(tapeReadWriteService.readFromTape(readOrder.getFileName()))
                .thenReturn(new TapeResponse(StatusCode.OK));
        when(tapeDriveService.getDriveCommandService())
                .thenReturn(mock(TapeDriveCommandService.class));
        when(tapeDriveService.getDriveCommandService().rewind())
                .thenReturn(new TapeResponse(StatusCode.OK));
        when(tapeDriveService.getDriveCommandService().goToPosition(anyInt(), anyBoolean()))
                .thenReturn(new TapeResponse(StatusCode.OK));
        when(tapeDriveService.getDriveCommandService().goToPosition(anyInt()))
                .thenReturn(new TapeResponse(StatusCode.OK));
        when(tapeCatalogService.receive(any(), any()))
                .thenReturn(Optional.of(appropriateTape));
        when(tapeDriveService.getTapeDriveConf().getIndex())
                .thenReturn(DRIVE_INDEX);
        when(tapeRobotPool.checkoutRobotService())
                .thenReturn(mock(TapeRobotService.class));
        when(tapeRobotPool.checkoutRobotService().getLoadUnloadService())
                .thenReturn(mock(TapeLoadUnloadService.class));
        when(tapeRobotPool.checkoutRobotService().getLoadUnloadService()
                .loadTape(anyInt(), anyInt())).thenReturn(new TapeResponse(StatusCode.KO));
        when(tapeRobotPool.checkoutRobotService().getLoadUnloadService()
                .unloadTape(anyInt(), anyInt())).thenReturn(new TapeResponse(StatusCode.OK));

        // Case one current t
        ReadWriteResult result = readTask.get();

        assertThat(result).isNotNull();
        assertThat(result.getOrderState()).isEqualTo(QueueState.READY);
        assertThat(result.getCurrentTape()).isNull();
    }

    @Test
    public void testReadTaskWhenCurrentTapeIsNotNullAndNotEligibleThenFatalWhenUnloadingTheCurrentTape() throws InterruptedException, QueueException {
        // When
        when(tapeDriveService.getTapeDriveConf()).thenAnswer(o -> mock(TapeDriveConf.class));
        TapeCatalog currentTape = new TapeCatalog()
                .setLibrary(FAKE_LIBRARY)
                .setCode("tape")
                .setCurrentLocation(new TapeLocation(DRIVE_INDEX, TapeLocationType.DRIVE))
                .setPreviousLocation(new TapeLocation(SLOT_INDEX + 1, TapeLocationType.SLOT));

        TapeCatalog appropriateTape = new TapeCatalog()
                .setLibrary(FAKE_LIBRARY)
                .setCode(FAKE_TAPE_CODE)
                .setCurrentLocation(new TapeLocation(SLOT_INDEX, TapeLocationType.SLOT));

        ReadOrder readOrder = new ReadOrder().setTapeCode(FAKE_TAPE_CODE)
                .setFilePosition(FILE_POSITION)
                .setFileName("file_test.tar");

        ReadTask readTask =
                new ReadTask(readOrder, currentTape, tapeRobotPool, tapeDriveService, tapeCatalogService);

        when(tapeReadWriteService.readFromTape(readOrder.getFileName()))
                .thenReturn(new TapeResponse(StatusCode.OK));
        when(tapeDriveService.getDriveCommandService())
                .thenReturn(mock(TapeDriveCommandService.class));
        when(tapeDriveService.getDriveCommandService().rewind())
                .thenReturn(new TapeResponse(StatusCode.OK));
        when(tapeDriveService.getDriveCommandService().goToPosition(anyInt(), anyBoolean()))
                .thenReturn(new TapeResponse(StatusCode.OK));
        when(tapeDriveService.getDriveCommandService().goToPosition(anyInt()))
                .thenReturn(new TapeResponse(StatusCode.OK));
        when(tapeCatalogService.receive(any(), any()))
                .thenReturn(Optional.of(appropriateTape));
        when(tapeDriveService.getTapeDriveConf().getIndex())
                .thenReturn(DRIVE_INDEX);
        when(tapeRobotPool.checkoutRobotService())
                .thenReturn(mock(TapeRobotService.class));
        when(tapeRobotPool.checkoutRobotService().getLoadUnloadService())
                .thenReturn(mock(TapeLoadUnloadService.class));
        when(tapeRobotPool.checkoutRobotService().getLoadUnloadService()
                .unloadTape(anyInt(), anyInt())).thenReturn(new TapeResponse(StatusCode.FATAL));

        // Case one current t
        ReadWriteResult result = readTask.get();

        assertThat(result).isNotNull();
        assertThat(result.getOrderState()).isEqualTo(QueueState.ERROR);
        assertThat(result.getCurrentTape()).isNotNull();
    }

    @Test
    public void testReadTaskWhenCurrentTapeIsNotNullAndNotEligibleThenKoWhenUnloadingTheCurrentTape() throws InterruptedException, QueueException {
        // When
        when(tapeDriveService.getTapeDriveConf()).thenAnswer(o -> mock(TapeDriveConf.class));
        TapeCatalog currentTape = new TapeCatalog()
                .setLibrary(FAKE_LIBRARY)
                .setCode("tape")
                .setCurrentLocation(new TapeLocation(DRIVE_INDEX, TapeLocationType.DRIVE))
                .setPreviousLocation(new TapeLocation(SLOT_INDEX + 1, TapeLocationType.SLOT));

        TapeCatalog appropriateTape = new TapeCatalog()
                .setLibrary(FAKE_LIBRARY)
                .setCode(FAKE_TAPE_CODE)
                .setCurrentLocation(new TapeLocation(SLOT_INDEX, TapeLocationType.SLOT));

        ReadOrder readOrder = new ReadOrder().setTapeCode(FAKE_TAPE_CODE)
                .setFilePosition(FILE_POSITION)
                .setFileName("file_test.tar");

        ReadTask readTask =
                new ReadTask(readOrder, currentTape, tapeRobotPool, tapeDriveService, tapeCatalogService);

        when(tapeReadWriteService.readFromTape(readOrder.getFileName()))
                .thenReturn(new TapeResponse(StatusCode.OK));
        when(tapeDriveService.getDriveCommandService())
                .thenReturn(mock(TapeDriveCommandService.class));
        when(tapeDriveService.getDriveCommandService().rewind())
                .thenReturn(new TapeResponse(StatusCode.OK));
        when(tapeDriveService.getDriveCommandService().goToPosition(anyInt(), anyBoolean()))
                .thenReturn(new TapeResponse(StatusCode.OK));
        when(tapeDriveService.getDriveCommandService().goToPosition(anyInt()))
                .thenReturn(new TapeResponse(StatusCode.OK));
        when(tapeCatalogService.receive(any(), any()))
                .thenReturn(Optional.of(appropriateTape));
        when(tapeDriveService.getTapeDriveConf().getIndex())
                .thenReturn(DRIVE_INDEX);
        when(tapeRobotPool.checkoutRobotService())
                .thenReturn(mock(TapeRobotService.class));
        when(tapeRobotPool.checkoutRobotService().getLoadUnloadService())
                .thenReturn(mock(TapeLoadUnloadService.class));
        when(tapeRobotPool.checkoutRobotService().getLoadUnloadService()
                .unloadTape(anyInt(), anyInt())).thenReturn(new TapeResponse(StatusCode.KO));

        // Case one current t
        ReadWriteResult result = readTask.get();

        assertThat(result).isNotNull();
        assertThat(result.getOrderState()).isEqualTo(QueueState.READY);
        assertThat(result.getCurrentTape()).isNotNull();
    }

    @Test
    public void testReadTaskWhenCurrentTapeIsNotEligibleAndLoadEligibleTapeThenFatalWhenReadIt() throws InterruptedException, QueueException {
        // When
        when(tapeDriveService.getTapeDriveConf()).thenAnswer(o -> mock(TapeDriveConf.class));
        TapeCatalog currentTape = new TapeCatalog()
                .setLibrary(FAKE_LIBRARY)
                .setCode("tape")
                .setCurrentLocation(new TapeLocation(DRIVE_INDEX, TapeLocationType.DRIVE))
                .setPreviousLocation(new TapeLocation(SLOT_INDEX + 1, TapeLocationType.SLOT));

        TapeCatalog appropriateTape = new TapeCatalog()
                .setLibrary(FAKE_LIBRARY)
                .setCode(FAKE_TAPE_CODE)
                .setCurrentLocation(new TapeLocation(SLOT_INDEX, TapeLocationType.SLOT));

        ReadOrder readOrder = new ReadOrder().setTapeCode(FAKE_TAPE_CODE)
                .setFilePosition(FILE_POSITION)
                .setFileName("file_test.tar");

        ReadTask readTask =
                new ReadTask(readOrder, currentTape, tapeRobotPool, tapeDriveService, tapeCatalogService);

        when(tapeReadWriteService.readFromTape(readOrder.getFileName()))
                .thenReturn(new TapeResponse(StatusCode.FATAL));
        when(tapeDriveService.getDriveCommandService())
                .thenReturn(mock(TapeDriveCommandService.class));
        when(tapeDriveService.getDriveCommandService().rewind())
                .thenReturn(new TapeResponse(StatusCode.OK));
        when(tapeDriveService.getDriveCommandService().goToPosition(anyInt(), anyBoolean()))
                .thenReturn(new TapeResponse(StatusCode.OK));
        when(tapeDriveService.getDriveCommandService().goToPosition(anyInt()))
                .thenReturn(new TapeResponse(StatusCode.OK));
        when(tapeCatalogService.receive(any(), any()))
                .thenReturn(Optional.of(appropriateTape));
        when(tapeDriveService.getTapeDriveConf().getIndex())
                .thenReturn(DRIVE_INDEX);
        when(tapeRobotPool.checkoutRobotService())
                .thenReturn(mock(TapeRobotService.class));
        when(tapeRobotPool.checkoutRobotService().getLoadUnloadService())
                .thenReturn(mock(TapeLoadUnloadService.class));
        when(tapeRobotPool.checkoutRobotService().getLoadUnloadService()
                .unloadTape(anyInt(), anyInt())).thenReturn(new TapeResponse(StatusCode.KO));

        // Case one current t
        ReadWriteResult result = readTask.get();

        assertThat(result).isNotNull();
        assertThat(result.getOrderState()).isEqualTo(QueueState.READY);
        assertThat(result.getCurrentTape()).isNotNull();
    }

    @Test
    public void testReadTaskWhenCurrentTapeIsNotEligibleAndLoadEligibleTapeThenKoWhenReadIt() throws InterruptedException, QueueException {
        // When
        when(tapeDriveService.getTapeDriveConf()).thenAnswer(o -> mock(TapeDriveConf.class));
        TapeCatalog currentTape = new TapeCatalog()
                .setLibrary(FAKE_LIBRARY)
                .setCode("tape")
                .setCurrentLocation(new TapeLocation(DRIVE_INDEX, TapeLocationType.DRIVE))
                .setPreviousLocation(new TapeLocation(SLOT_INDEX + 1, TapeLocationType.SLOT));

        TapeCatalog appropriateTape = new TapeCatalog()
                .setLibrary(FAKE_LIBRARY)
                .setCode(FAKE_TAPE_CODE)
                .setCurrentLocation(new TapeLocation(SLOT_INDEX, TapeLocationType.SLOT));

        ReadOrder readOrder = new ReadOrder().setTapeCode(FAKE_TAPE_CODE)
                .setFilePosition(FILE_POSITION)
                .setFileName("file_test.tar");

        ReadTask readTask =
                new ReadTask(readOrder, currentTape, tapeRobotPool, tapeDriveService, tapeCatalogService);

        when(tapeReadWriteService.readFromTape(readOrder.getFileName()))
                .thenReturn(new TapeResponse(StatusCode.KO));
        when(tapeDriveService.getDriveCommandService())
                .thenReturn(mock(TapeDriveCommandService.class));
        when(tapeDriveService.getDriveCommandService().rewind())
                .thenReturn(new TapeResponse(StatusCode.OK));
        when(tapeDriveService.getDriveCommandService().goToPosition(anyInt(), anyBoolean()))
                .thenReturn(new TapeResponse(StatusCode.OK));
        when(tapeDriveService.getDriveCommandService().goToPosition(anyInt()))
                .thenReturn(new TapeResponse(StatusCode.OK));
        when(tapeCatalogService.receive(any(), any()))
                .thenReturn(Optional.of(appropriateTape));
        when(tapeDriveService.getTapeDriveConf().getIndex())
                .thenReturn(DRIVE_INDEX);
        when(tapeRobotPool.checkoutRobotService())
                .thenReturn(mock(TapeRobotService.class));
        when(tapeRobotPool.checkoutRobotService().getLoadUnloadService())
                .thenReturn(mock(TapeLoadUnloadService.class));
        when(tapeRobotPool.checkoutRobotService().getLoadUnloadService()
                .unloadTape(anyInt(), anyInt())).thenReturn(new TapeResponse(StatusCode.KO));

        // Case one current t
        ReadWriteResult result = readTask.get();

        assertThat(result).isNotNull();
        assertThat(result.getOrderState()).isEqualTo(QueueState.READY);
        assertThat(result.getCurrentTape()).isNotNull();
    }

    @Test
    public void testReadTaskCurrentTapeIsNullAndEligibleTapeFound() throws QueueException, InterruptedException {
        // When
        when(tapeDriveService.getTapeDriveConf()).thenAnswer(o -> mock(TapeDriveConf.class));
        TapeCatalog tapeCatalog = new TapeCatalog()
                .setLibrary(FAKE_LIBRARY)
                .setCode(FAKE_TAPE_CODE)
                .setCurrentLocation(new TapeLocation(SLOT_INDEX, TapeLocationType.SLOT));

        ReadOrder readOrder = new ReadOrder().setTapeCode(FAKE_TAPE_CODE)
                .setFilePosition(FILE_POSITION)
                .setFileName("file_test.tar");

        ReadTask readTask =
                new ReadTask(readOrder, null, tapeRobotPool, tapeDriveService, tapeCatalogService);

        when(tapeReadWriteService.readFromTape(readOrder.getFileName()))
                .thenReturn(new TapeResponse(StatusCode.OK));
        when(tapeDriveService.getDriveCommandService())
                .thenReturn(mock(TapeDriveCommandService.class));
        when(tapeDriveService.getDriveCommandService().rewind())
                .thenReturn(new TapeResponse(StatusCode.OK));
        when(tapeDriveService.getDriveCommandService().goToPosition(anyInt(), anyBoolean()))
                .thenReturn(new TapeResponse(StatusCode.OK));
        when(tapeDriveService.getDriveCommandService().goToPosition(anyInt()))
                .thenReturn(new TapeResponse(StatusCode.OK));
        when(tapeCatalogService.receive(any(), any()))
                .thenReturn(Optional.of(tapeCatalog));
        when(tapeDriveService.getTapeDriveConf().getIndex())
                .thenReturn(DRIVE_INDEX);
        when(tapeRobotPool.checkoutRobotService())
                .thenReturn(mock(TapeRobotService.class));
        when(tapeRobotPool.checkoutRobotService().getLoadUnloadService())
                .thenReturn(mock(TapeLoadUnloadService.class));
        when(tapeRobotPool.checkoutRobotService().getLoadUnloadService()
                .loadTape(anyInt(), anyInt())).thenReturn(new TapeResponse(StatusCode.OK));

        // Case one current t
        ReadWriteResult result = readTask.get();

        assertThat(result).isNotNull();
        assertThat(result.getOrderState()).isEqualTo(QueueState.COMPLETED);
        assertThat(result.getCurrentTape()).isNotNull();
        assertThat(result.getCurrentTape().getCode()).isEqualTo(FAKE_TAPE_CODE);
        assertThat(result.getCurrentTape().getCurrentLocation()
                .equals(new TapeLocation(DRIVE_INDEX, TapeLocationType.DRIVE))).isEqualTo(true);
        assertThat(result.getCurrentTape().getCurrentPosition()).isEqualTo(FILE_POSITION + 1);

    }
}