/*******************************************************************************
 * Copyright (C) 2018, OpenRefine contributors
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/

package org.openrefine.operations.recon;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.Serializable;
import java.util.Collections;

import org.testng.annotations.BeforeSuite;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import org.openrefine.RefineTest;
import org.openrefine.browsing.EngineConfig;
import org.openrefine.model.Cell;
import org.openrefine.model.ColumnMetadata;
import org.openrefine.model.ColumnModel;
import org.openrefine.model.GridState;
import org.openrefine.model.ModelException;
import org.openrefine.model.changes.Change;
import org.openrefine.model.changes.Change.DoesNotApplyException;
import org.openrefine.model.changes.ChangeContext;
import org.openrefine.model.recon.Recon;
import org.openrefine.model.recon.Recon.Judgment;
import org.openrefine.model.recon.ReconConfig;
import org.openrefine.model.recon.ReconStats;
import org.openrefine.model.recon.StandardReconConfig;
import org.openrefine.operations.OperationRegistry;
import org.openrefine.util.ParsingUtilities;
import org.openrefine.util.TestUtils;

public class ReconMarkNewTopicsOperationTests extends RefineTest {

    private GridState initialState;
    private ReconConfig reconConfig;

    @BeforeSuite
    public void registerOperation() {
        OperationRegistry.registerOperation("core", "recon-mark-new-topics", ReconMarkNewTopicsOperation.class);
    }

    @BeforeTest
    public void setupInitialState() throws ModelException {
        reconConfig = new StandardReconConfig("http://my.service.com/api",
                "http://my.service.com/identifierSpace",
                "http://my.service.com/schemaSpace",
                null,
                true,
                Collections.emptyList(),
                5);

        initialState = createGrid(
                new String[] { "foo", "bar" },
                new Serializable[][] {
                        { "a", new Cell("b", testRecon("e", "h", Recon.Judgment.Matched).withId(1L)) },
                        { "c", new Cell("b", testRecon("x", "p", Recon.Judgment.New).withId(2L)) },
                        { "c", new Cell("d", testRecon("b", "j", Recon.Judgment.None)) }
                });
        ColumnModel columnModel = initialState.getColumnModel();
        initialState = initialState.withColumnModel(columnModel.withReconConfig(1, reconConfig));
    }

    @Test
    public void serializeReconMarkNewTopicsOperation() throws Exception {
        String json = "{"
                + "\"op\":\"core/recon-mark-new-topics\","
                + "\"engineConfig\":{\"mode\":\"row-based\",\"facets\":[]},"
                + "\"columnName\":\"my column\","
                + "\"shareNewTopics\":true,"
                + "\"description\":\"Mark to create new items for cells in column my column, one item for each group of similar cells\""
                + "}";
        TestUtils.isSerializedTo(ParsingUtilities.mapper.readValue(json, ReconMarkNewTopicsOperation.class), json,
                ParsingUtilities.defaultWriter);
    }

    @Test
    public void testReconMarkNewTopicsOperation() throws DoesNotApplyException, ModelException {
        Change change = new ReconMarkNewTopicsOperation(EngineConfig.ALL_ROWS, "bar", true).createChange();

        ChangeContext context = mock(ChangeContext.class);
        when(context.getHistoryEntryId()).thenReturn(2891L);

        GridState applied = change.apply(initialState, context);

        long commonReconId = applied.collectRows().get(0).getRow().getCell(1).recon.id;
        long otherReconId = applied.collectRows().get(2).getRow().getCell(1).recon.id;

        GridState expected = createGrid(
                new String[] { "foo", "bar" },
                new Serializable[][] {
                        { "a", new Cell("b", reconConfig.createNewRecon(2891L)
                                .withId(commonReconId)
                                .withJudgmentAction("mass")
                                .withJudgment(Judgment.New)) },
                        { "c", new Cell("b", reconConfig.createNewRecon(2891L)
                                .withId(commonReconId)
                                .withJudgmentAction("mass")
                                .withJudgment(Judgment.New)) },
                        { "c", new Cell("d", reconConfig.createNewRecon(2891L)
                                .withId(otherReconId)
                                .withJudgmentAction("mass")
                                .withJudgment(Judgment.New)) }
                });

        // Make sure recon stats are updated too
        ReconStats reconStats = ReconStats.create(3, 3, 0);
        ColumnModel columnModel = expected.getColumnModel();
        ColumnMetadata columnMetadata = columnModel.getColumnByName("bar").withReconConfig(reconConfig);
        expected = expected.withColumnModel(columnModel.replaceColumn(1, columnMetadata.withReconStats(reconStats)));

        assertGridEquals(applied, expected);
    }

    @Test
    public void testReconJudgeSimilarCellsIndividually() throws DoesNotApplyException, ModelException {
        Change change = new ReconMarkNewTopicsOperation(EngineConfig.ALL_ROWS, "bar", false).createChange();

        ChangeContext context = mock(ChangeContext.class);
        when(context.getHistoryEntryId()).thenReturn(2891L);

        GridState applied = change.apply(initialState, context);

        long firstReconId = applied.collectRows().get(0).getRow().getCell(1).recon.id;
        long secondReconId = applied.collectRows().get(1).getRow().getCell(1).recon.id;
        long thirdReconId = applied.collectRows().get(2).getRow().getCell(1).recon.id;

        GridState expected = createGrid(
                new String[] { "foo", "bar" },
                new Serializable[][] {
                        { "a", new Cell("b", testRecon("e", "h", Recon.Judgment.New)
                                .withJudgmentHistoryEntry(2891L)
                                .withId(firstReconId)
                                .withJudgmentAction("mass")) },
                        { "c", new Cell("b", testRecon("x", "p", Recon.Judgment.New)
                                .withJudgmentHistoryEntry(2891L)
                                .withId(secondReconId)
                                .withJudgmentAction("mass")) },
                        { "c", new Cell("d", testRecon("b", "j", Recon.Judgment.New)
                                .withJudgmentHistoryEntry(2891L)
                                .withId(thirdReconId)
                                .withJudgmentAction("mass")) }
                });

        // Make sure recon stats are updated too
        ReconStats reconStats = ReconStats.create(3, 3, 0);
        ColumnModel columnModel = expected.getColumnModel();
        ColumnMetadata columnMetadata = columnModel.getColumnByName("bar").withReconConfig(reconConfig);
        expected = expected.withColumnModel(columnModel.replaceColumn(1, columnMetadata.withReconStats(reconStats)));

        assertGridEquals(applied, expected);
    }
}