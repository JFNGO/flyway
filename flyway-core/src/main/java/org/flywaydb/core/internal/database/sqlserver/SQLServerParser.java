/*
 * Copyright 2010-2019 Boxfuse GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.flywaydb.core.internal.database.sqlserver;

import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.internal.parser.*;
import org.flywaydb.core.internal.sqlscript.Delimiter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class SQLServerParser extends Parser {
    // #2175, 2298, 2542: Various system sprocs, mostly around replication, cannot be executed within a transaction.
    // These procedures are only present in SQL Server. Not on Azure nor in PDW.
    private static final List<String> SPROCS_INVALID_IN_TRANSACTIONS = Arrays.asList(
            "SP_ADDSUBSCRIPTION", "SP_DROPSUBSCRIPTION",
            "SP_ADDDISTRIBUTOR", "SP_DROPDISTRIBUTOR",
            "SP_ADDDISTPUBLISHER", "SP_DROPDISTPUBLISHER",
            "SP_ADDLINKEDSERVER", "SP_DROPLINKEDSERVER",
            "SP_ADDLINKEDSRVLOGIN", "SP_DROPLINKEDSRVLOGIN",
            "SP_SERVEROPTION", "SP_REPLICATIONDBOPTION");

    public SQLServerParser(Configuration configuration, ParsingContext parsingContext) {
        super(configuration, parsingContext, 3);
    }

    @Override
    protected Delimiter getDefaultDelimiter() {
        return Delimiter.GO;
    }

    @Override
    protected boolean isDelimiter(String peek, ParserContext context) {
        String delimiterText = super.getDelimiter().getDelimiter().toUpperCase();
        int delimiterTextLength = delimiterText.length();
        return (peek.length() >= delimiterTextLength
                && peek.toUpperCase().startsWith(delimiterText)
                && (peek.length() == delimiterTextLength || Character.isWhitespace(peek.charAt(delimiterTextLength))));
    }

    @Override
    protected String readKeyword(PeekingReader reader, Delimiter delimiter) throws IOException {
        // #2414: Ignore delimiter as GO (unlike ;) can be part of a regular keyword
        return "" + (char) reader.read() + reader.readKeywordPart(null);
    }

    @Override
    protected Boolean detectCanExecuteInTransaction(String simplifiedStatement, List<Token> keywords) {
        String current = keywords.get(keywords.size() - 1).getText();
        if ("BACKUP".equals(current) || "RESTORE".equals(current) || "RECONFIGURE".equals(current)) {
            return false;
        }

        if (keywords.size() < 2) {
            return null;
        }

        String previous = keywords.get(keywords.size() - 2).getText();

        if ("EXEC".equals(previous) && SPROCS_INVALID_IN_TRANSACTIONS.contains(current)) {
            return false;
        }

        // (CREATE|DROP|ALTER) (DATABASE|FULLTEXT (INDEX|CATALOG))
        if (("CREATE".equals(previous) || "ALTER".equals(previous) || "DROP".equals(previous))
                && ("DATABASE".equals(current) || "FULLTEXT".equals(current))) {
            return false;
        }

        return null;
    }

    @Override
    protected int getTransactionalDetectionCutoff() {
        return Integer.MAX_VALUE;
    }
}