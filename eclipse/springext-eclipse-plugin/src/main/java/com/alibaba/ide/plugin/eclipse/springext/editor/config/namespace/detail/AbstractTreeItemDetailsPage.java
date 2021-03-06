package com.alibaba.ide.plugin.eclipse.springext.editor.config.namespace.detail;

import static com.alibaba.citrus.util.ArrayUtil.*;
import static com.alibaba.citrus.util.Assert.*;

import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ITreeSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.forms.IDetailsPage;
import org.eclipse.ui.forms.IFormPart;
import org.eclipse.ui.forms.IManagedForm;
import org.eclipse.ui.forms.widgets.ExpandableComposite;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.Section;
import org.eclipse.ui.forms.widgets.TableWrapLayout;

import com.alibaba.citrus.springext.support.SpringExtSchemaSet.TreeItem;
import com.alibaba.ide.plugin.eclipse.springext.editor.config.SpringExtConfigData;
import com.alibaba.ide.plugin.eclipse.springext.editor.config.namespace.NamespacesMasterPart;

public abstract class AbstractTreeItemDetailsPage<T extends TreeItem> implements IDetailsPage {
    protected IManagedForm form;
    protected FormToolkit toolkit;
    protected T item;
    protected SpringExtConfigData config;
    protected Section section;
    protected Composite client;

    public void initialize(IManagedForm form) {
        this.form = form;
        this.toolkit = form.getToolkit();
        this.config = getNamespacesMasterPart().getData();
    }

    private NamespacesMasterPart getNamespacesMasterPart() {
        return assertNotNull(SpringExtConfigData.getFormPart(NamespacesMasterPart.class, form),
                "no Namespaces master part found");
    }

    public final void createContents(Composite parent) {
        GridLayout layout = new GridLayout(1, false);
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        parent.setLayout(layout);

        // section
        section = toolkit.createSection(parent, Section.DESCRIPTION | ExpandableComposite.TITLE_BAR);
        section.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        section.marginWidth = 10;
        section.marginHeight = 5;
        section.clientVerticalSpacing = 8;

        // 和master part的description对齐。
        section.descriptionVerticalSpacing = getNamespacesMasterPart().getSection().getTextClientHeightDifference();

        // section/client
        client = toolkit.createComposite(section);
        TableWrapLayout tlayout = new TableWrapLayout();
        tlayout.numColumns = 2;
        tlayout.horizontalSpacing = 20;
        tlayout.verticalSpacing = 20;
        client.setLayout(tlayout);

        section.setClient(client);

        initSection();
    }

    protected abstract void initSection();

    public void dispose() {
    }

    public boolean isDirty() {
        return false;
    }

    public void commit(boolean onSave) {
    }

    public boolean setFormInput(Object input) {
        return false;
    }

    public void setFocus() {
    }

    public boolean isStale() {
        return false;
    }

    public void refresh() {
        update();
    }

    @SuppressWarnings("unchecked")
    public void selectionChanged(IFormPart part, ISelection selection) {
        ITreeSelection ts = (ITreeSelection) selection;

        if (!isEmptyArray(ts.getPaths())) {
            this.item = (T) ts.getPaths()[0].getLastSegment();
        } else {
            this.item = null;
        }

        update();
    }

    protected abstract void update();
}
