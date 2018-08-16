package ru.mail.jira.plugins.groovy.impl.jql.function.builtin;

import com.atlassian.jira.issue.index.DocumentConstants;
import com.google.common.collect.ImmutableSet;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.FieldSelector;
import org.apache.lucene.document.SetBasedFieldSelector;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.Collector;
import org.apache.lucene.search.Scorer;
import ru.mail.jira.plugins.groovy.impl.jql.indexers.CustomIssueLinkIndexer;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

//todo: consider limiting result set
public class LinkedIssueCollector extends Collector {
    public static final Filter ACCEPT_ALL = (it) -> true;

    private static final FieldSelector fieldSelector = new SetBasedFieldSelector(
        ImmutableSet.of(DocumentConstants.ISSUE_ID, DocumentConstants.ISSUE_KEY, CustomIssueLinkIndexer.FIELD_KEY),
        ImmutableSet.of()
    );

    private final Set<String> issueIds = new HashSet<>();
    private final Filter filter;

    private IndexReader reader;

    public LinkedIssueCollector(Filter filter) {
        this.filter = filter;
    }

    @Override
    public void setScorer(Scorer scorer) {

    }

    @Override
    public void collect(int i) throws IOException {
        Document document = reader.document(i, fieldSelector);

        for (String value : document.getValues(CustomIssueLinkIndexer.FIELD_KEY)) {
            if (filter.test(value)) {
                issueIds.add(value.substring(value.indexOf("i:") + 2));
            }
        }
    }

    @Override
    public void setNextReader(IndexReader indexReader, int docBase) {
        this.reader = indexReader;
    }

    @Override
    public boolean acceptsDocsOutOfOrder() {
        return true;
    }

    public Set<String> getIssueIds() {
        return this.issueIds;
    }

    public interface Filter {
        boolean test(String value);
    }
}
