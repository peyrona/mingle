// TableEditor Unit Tests
// Test framework: Jasmine or Mocha expected (adapt to your test framework)

describe('TableEditor', () =>
{
    let tableElement;
    let toolbarElement;

    beforeEach(() =>
    {
        tableElement = document.createElement('table');
        toolbarElement = document.createElement('div');
        document.body.appendChild( tableElement );
        document.body.appendChild( toolbarElement );
    });

    afterEach(() =>
    {
        document.body.innerHTML = '';
    });

    describe('Initialization', () =>
    {
        it('should throw error if table parameter missing', () =>
        {
            expect(() => new TableEditor({}))
                .toThrow();
        });

        it('should throw error if table is not found', () =>
        {
            expect(() => new TableEditor({ table: '#nonexistent-table' }))
                .toThrow();
        });

        it('should initialize with valid parameters', () =>
        {
            const editor = new TableEditor({
                table: tableElement,
                columns: [
                    { name: 'id', default: 0 },
                    { name: 'name', editor: '<input type="text">' }
                ]
            });

            expect( editor.tbody ).not.toBeNull();
            expect( editor.getBodyRowCount() ).toBe(0);
        });

        it('should accept custom row colors', () =>
        {
            const editor = new TableEditor({
                table: tableElement,
                toolbar: toolbarElement,
                selectedRowInk: '#ffffff',
                selectedRowPaper: '#000000',
                columns: [
                    { name: 'id', editor: '<input type="text">' }
                ]
            });

            expect( editor.selectedRowInk ).toBe('#ffffff');
            expect( editor.selectedRowPaper ).toBe('#000000');
        });

        it('should accept keyboard navigation config', () =>
        {
            const editor = new TableEditor({
                table: tableElement,
                enableKeyboardNav: false,
                columns: [
                    { name: 'id', editor: '<input type="text">' }
                ]
            });

            expect( editor.enableKeyboardNav ).toBe(false);
        });

        it('should initialize with toolbar', () =>
        {
            const editor = new TableEditor({
                table: tableElement,
                toolbar: toolbarElement,
                columns: [
                    { name: 'id', editor: '<input type="text">' }
                ]
            });

            expect( editor.toolbar ).not.toBeNull();
            expect( toolbarElement.querySelectorAll('.mini-btn').length ).toBeGreaterThan(0);
        });

        it('should setup ARIA attributes', () =>
        {
            new TableEditor({
                table: tableElement,
                columns: [
                    { name: 'id', editor: '<input type="text">' }
                ]
            });

            expect( tableElement.getAttribute('role') ).toBe('grid');
            expect( tableElement.getAttribute('aria-label') ).toBe('Editable data table');
        });
    });

    describe('Row Operations', () =>
    {
        it('should append empty row', () =>
        {
            const editor = new TableEditor({
                table: tableElement,
                toolbar: toolbarElement,
                columns: [
                    { name: 'id', default: 0 },
                    { name: 'name', editor: '<input type="text">' }
                ]
            });

            editor.appendRow();

            expect( editor.getBodyRowCount() ).toBe(1);
        });

        it('should append row with default values', () =>
        {
            const editor = new TableEditor({
                table: tableElement,
                columns: [
                    { name: 'id', default: 100 },
                    { name: 'name', editor: '<input type="text">' }
                ]
            });

            editor.appendRow();
            const data = editor.getRowData( 0 );

            expect( data.id ).toBe(100);
        });

        it('should delete selected row', () =>
        {
            const editor = new TableEditor({
                table: tableElement,
                toolbar: toolbarElement,
                columns: [
                    { name: 'id', editor: '<input type="text">' },
                    { name: 'name', editor: '<input type="text">' }
                ]
            });

            editor.setData([{ id: 1, name: 'Test' }]);
            editor.deleteRow();

            expect( editor.getBodyRowCount() ).toBe(0);
        });

        it('should delete row by index', () =>
        {
            const editor = new TableEditor({
                table: tableElement,
                toolbar: toolbarElement,
                columns: [
                    { name: 'id', editor: '<input type="text">' },
                    { name: 'name', editor: '<input type="text">' }
                ]
            });

            editor.setData([{ id: 1, name: 'Row1' }, { id: 2, name: 'Row2' }]);
            editor.deleteRow( 0 );

            expect( editor.getBodyRowCount() ).toBe(1);
            expect( editor.getRowData( 0 ).id ).toBe(2);
        });

        it('should clone selected row', () =>
        {
            const editor = new TableEditor({
                table: tableElement,
                toolbar: toolbarElement,
                columns: [
                    { name: 'id', editor: '<input type="text">' },
                    { name: 'name', editor: '<input type="text">' }
                ]
            });

            editor.setData([{ id: 1, name: 'Test' }]);
            editor.cloneRow();

            expect( editor.getBodyRowCount() ).toBe(2);
            expect( editor.getRowData( 1 ).name ).toBe('Test');
        });

        it('should not clone if no row selected', () =>
        {
            const editor = new TableEditor({
                table: tableElement,
                toolbar: toolbarElement,
                columns: [
                    { name: 'id', editor: '<input type="text">' }
                ]
            });

            editor.cloneRow();

            expect( editor.getBodyRowCount() ).toBe(0);
        });

        it('should clear all rows', () =>
        {
            const editor = new TableEditor({
                table: tableElement,
                columns: [
                    { name: 'id', editor: '<input type="text">' }
                ]
            });

            editor.setData([{ id: 1 }, { id: 2 }, { id: 3 }]);
            editor.clear();

            expect( editor.getBodyRowCount() ).toBe(0);
        });

        it('should clean empty rows', () =>
        {
            const editor = new TableEditor({
                table: tableElement,
                columns: [
                    { name: 'id', default: 0 },
                    { name: 'name', editor: '<input type="text">' }
                ]
            });

            editor.setData([
                { id: 1, name: 'Row1' },
                { id: 0, name: '' },
                { id: 2, name: 'Row2' }
            ]);
            editor.clean();

            expect( editor.getBodyRowCount() ).toBe(2);
        });
    });

    describe('Cell Editing', () =>
    {
        it('should start editing on double click', (done) =>
        {
            const editor = new TableEditor({
                table: tableElement,
                columns: [
                    { name: 'id', editor: '<input type="text">' }
                ]
            });

            editor.setData([{ id: 1 }]);

            setTimeout(() =>
            {
                const cell = editor.getCell( 0, 0 );
                editor.editCell( cell );

                expect( editor.getOngoingCellEditor() ).not.toBeNull();
                done();
            }, 100);
        });

        it('should validate input before saving', (done) =>
        {
            const editor = new TableEditor({
                table: tableElement,
                columns: [
                    { name: 'email', editor: '<input type="text">' }
                ],
                onCellPreEdit: function(tableEditor, rowIndex, colName, value) {
                    return null; // Cancel editing
                }
            });

            editor.setData([{ email: 'test@example.com' }]);
            const cell = editor.getCell( 0, 0 );

            editor.editCell( cell );

            setTimeout(() =>
            {
                expect( editor.getOngoingCellEditor() ).toBeNull();
                done();
            }, 100);
        });

        it('should update cell value after editing', (done) =>
        {
            const editor = new TableEditor({
                table: tableElement,
                columns: [
                    { name: 'name', editor: '<input type="text">' }
                ]
            });

            editor.setData([{ name: 'Old Value' }]);
            const cell = editor.getCell( 0, 0 );

            editor.editCell( cell );

            setTimeout(() =>
            {
                const input = editor.getOngoingCellEditor();
                $(input).val( 'New Value' );
                $(input).blur();

                setTimeout(() =>
                {
                    expect( editor.getValue( 0, 0 ) ).toBe('New Value');
                    done();
                }, 100);
            }, 100);
        });
    });

    describe('Data Management', () =>
    {
        it('should return all row data', () =>
        {
            const editor = new TableEditor({
                table: tableElement,
                columns: [
                    { name: 'id', editor: '<input type="text">' },
                    { name: 'name', editor: '<input type="text">' }
                ]
            });

            editor.setData([
                { id: 1, name: 'Row1' },
                { id: 2, name: 'Row2' }
            ]);

            const data = editor.getData( false );

            expect( data.length ).toBe(2);
            expect( data[0].id ).toBe(1);
            expect( data[1].name ).toBe('Row2');
        });

        it('should clean before returning data', () =>
        {
            const editor = new TableEditor({
                table: tableElement,
                columns: [
                    { name: 'id', default: 0 },
                    { name: 'name', editor: '<input type="text">' }
                ]
            });

            editor.setData([
                { id: 1, name: 'Row1' },
                { id: 0, name: '' }
            ]);

            const data = editor.getData( true );

            expect( data.length ).toBe(1);
        });

        it('should set data correctly', () =>
        {
            const editor = new TableEditor({
                table: tableElement,
                columns: [
                    { name: 'id', editor: '<input type="text">' },
                    { name: 'name', editor: '<input type="text">' }
                ]
            });

            const testData = [
                { id: 1, name: 'Row1' },
                { id: 2, name: 'Row2' }
            ];

            editor.setData( testData );

            expect( editor.getBodyRowCount() ).toBe(2);
            expect( editor.getRowData( 0 ).id ).toBe(1);
        });

        it('should track deleted rows', () =>
        {
            const editor = new TableEditor({
                table: tableElement,
                columns: [
                    { name: 'id', editor: '<input type="text">' },
                    { name: 'name', editor: '<input type="text">' }
                ]
            });

            editor.setData([{ id: 1, name: 'Row1' }]);
            editor.deleteRow();

            const deletedData = editor.getDeletedData();

            expect( deletedData.length ).toBe(1);
            expect( deletedData[0].id ).toBe(1);
        });
    });

    describe('Row Selection', () =>
    {
        it('should select row by index', () =>
        {
            const editor = new TableEditor({
                table: tableElement,
                columns: [
                    { name: 'id', editor: '<input type="text">' }
                ]
            });

            editor.setData([{ id: 1 }, { id: 2 }]);
            editor.selectRowAtIndex( 1 );

            expect( editor.getSelectedRowIndex() ).toBe(1);
        });

        it('should return selected row', () =>
        {
            const editor = new TableEditor({
                table: tableElement,
                columns: [
                    { name: 'id', editor: '<input type="text">' }
                ]
            });

            editor.setData([{ id: 1 }, { id: 2 }]);
            editor.selectRowAtIndex( 1 );

            const selectedRow = editor.getSelectedRow();

            expect( selectedRow ).not.toBeNull();
            expect( editor.getValue( 0, 0 ) ).toBe(2);
        });

        it('should update ARIA on selection', () =>
        {
            const editor = new TableEditor({
                table: tableElement,
                columns: [
                    { name: 'id', editor: '<input type="text">' }
                ]
            });

            editor.setData([{ id: 1 }, { id: 2 }]);
            editor.selectRowAtIndex( 0 );

            const rows = editor.tbody.rows;
            expect( rows[0].getAttribute('aria-selected') ).toBe('true');
            expect( rows[1].getAttribute('aria-selected') ).toBe('false');
        });
    });

    describe('Cell Operations', () =>
    {
        it('should get cell value', () =>
        {
            const editor = new TableEditor({
                table: tableElement,
                columns: [
                    { name: 'id', editor: '<input type="text">' },
                    { name: 'name', editor: '<input type="text">' }
                ]
            });

            editor.setData([{ id: 123, name: 'Test' }]);

            expect( editor.getValue( 0, 0 ) ).toBe(123);
            expect( editor.getValue( 0, 1 ) ).toBe('Test');
        });

        it('should get cell by index and name', () =>
        {
            const editor = new TableEditor({
                table: tableElement,
                columns: [
                    { name: 'id', editor: '<input type="text">' },
                    { name: 'name', editor: '<input type="text">' }
                ]
            });

            editor.setData([{ id: 123, name: 'Test' }]);

            expect( editor.getCell( 0, 'name' ).not.toBeNull();
            expect( editor.getCell( 0, 1 ) ).not.toBeNull();
        });

        it('should set cell value', () =>
        {
            const editor = new TableEditor({
                table: tableElement,
                columns: [
                    { name: 'id', editor: '<input type="text">' }
                ]
            });

            editor.setData([{ id: 1 }]);
            editor.setValue( 0, 0, 999 );

            expect( editor.getValue( 0, 0 ) ).toBe(999);
        });

        it('should handle invalid row index gracefully', () =>
        {
            const editor = new TableEditor({
                table: tableElement,
                columns: [
                    { name: 'id', editor: '<input type="text">' }
                ]
            });

            editor.setData([{ id: 1 }]);

            const data = editor.getRowData( 999 );

            expect( data ).toEqual({});
        });
    });

    describe('Button Management', () =>
    {
        it('should disable all buttons', () =>
        {
            const editor = new TableEditor({
                table: tableElement,
                toolbar: toolbarElement,
                columns: [
                    { name: 'id', editor: '<input type="text">' }
                ],
                onSave: function() {}
            });

            editor.setEnabled( false );

            const buttons = toolbarElement.querySelectorAll('.mini-btn, button');

            for( let i = 0; i < buttons.length; i++ )
            {
                expect( buttons[i].disabled ).toBe(true);
            }
        });

        it('should enable specific buttons', () =>
        {
            const editor = new TableEditor({
                table: tableElement,
                toolbar: toolbarElement,
                columns: [
                    { name: 'id', editor: '<input type="text">' }
                ]
            });

            editor.setButtonAppendEnabled( false );
            editor.setButtonDeleteEnabled( false );

            expect( editor.isBtnAppendEnabled ).toBe(false);
            expect( editor.isBtnDeleteEnabled ).toBe(false);
        });

        it('should append custom button', () =>
        {
            const editor = new TableEditor({
                table: tableElement,
                toolbar: toolbarElement,
                columns: [
                    { name: 'id', editor: '<input type="text">' }
                ]
            });

            let buttonClicked = false;
            const customBtn = editor.appendButton(
                'Custom',
                'Custom Button',
                'fa-star',
                function(tableEditor) { buttonClicked = true; }
            );

            expect( customBtn ).not.toBeNull();
            customBtn.click();

            expect( buttonClicked ).toBe(true);
        });
    });

    describe('Accessibility', () =>
    {
        it('should have grid role', () =>
        {
            new TableEditor({
                table: tableElement,
                columns: [
                    { name: 'id', editor: '<input type="text">' }
                ]
            });

            expect( tableElement.getAttribute('role') ).toBe('grid');
        });

        it('should have aria-label', () =>
        {
            new TableEditor({
                table: tableElement,
                columns: [
                    { name: 'id', editor: '<input type="text">' }
                ]
            });

            expect( tableElement.getAttribute('aria-label') ).toBe('Editable data table');
        });

        it('should have aria-rowindex on rows', () =>
        {
            const editor = new TableEditor({
                table: tableElement,
                columns: [
                    { name: 'id', editor: '<input type="text">' }
                ]
            });

            editor.setData([{ id: 1 }, { id: 2 }]);

            const rows = editor.tbody.rows;
            expect( rows[0].getAttribute('aria-rowindex') ).toBe('1');
            expect( rows[1].getAttribute('aria-rowindex') ).toBe('2');
        });

        it('should have aria-selected on rows', () =>
        {
            const editor = new TableEditor({
                table: tableElement,
                columns: [
                    { name: 'id', editor: '<input type="text">' }
                ]
            });

            editor.setData([{ id: 1 }, { id: 2 }]);
            editor.selectRowAtIndex( 0 );

            const rows = editor.tbody.rows;
            expect( rows[0].getAttribute('aria-selected') ).toBe('true');
            expect( rows[1].getAttribute('aria-selected') ).toBe('false');
        });
    });

    describe('Keyboard Navigation', () =>
    {
        it('should navigate with arrow keys', (done) =>
        {
            const editor = new TableEditor({
                table: tableElement,
                columns: [
                    { name: 'id', editor: '<input type="text">' }
                ]
            });

            editor.setData([{ id: 1 }, { id: 2 }, { id: 3 }]);
            editor.selectRowAtIndex( 0 );

            const downEvent = new KeyboardEvent('keydown', { keyCode: 40 });
            editor.tbody.dispatchEvent( downEvent );

            setTimeout(() =>
            {
                expect( editor.getSelectedRowIndex() ).toBe(1);
                done();
            }, 100);
        });

        it('should navigate to first row with Home key', (done) =>
        {
            const editor = new TableEditor({
                table: tableElement,
                columns: [
                    { name: 'id', editor: '<input type="text">' }
                ]
            });

            editor.setData([{ id: 1 }, { id: 2 }, { id: 3 }]);
            editor.selectRowAtIndex( 2 );

            const homeEvent = new KeyboardEvent('keydown', { keyCode: 36 });
            editor.tbody.dispatchEvent( homeEvent );

            setTimeout(() =>
            {
                expect( editor.getSelectedRowIndex() ).toBe(0);
                done();
            }, 100);
        });

        it('should navigate to last row with End key', (done) =>
        {
            const editor = new TableEditor({
                table: tableElement,
                columns: [
                    { name: 'id', editor: '<input type="text">' }
                ]
            });

            editor.setData([{ id: 1 }, { id: 2 }, { id: 3 }]);
            editor.selectRowAtIndex( 0 );

            const endEvent = new KeyboardEvent('keydown', { keyCode: 35 });
            editor.tbody.dispatchEvent( endEvent );

            setTimeout(() =>
            {
                expect( editor.getSelectedRowIndex() ).toBe(2);
                done();
            }, 100);
        });

        it('should not navigate while editing', (done) =>
        {
            const editor = new TableEditor({
                table: tableElement,
                columns: [
                    { name: 'id', editor: '<input type="text">' }
                ]
            });

            editor.setData([{ id: 1 }]);
            editor.selectRowAtIndex( 0 );

            editor.editCell( editor.getCell( 0, 0 ) );

            setTimeout(() =>
            {
                const downEvent = new KeyboardEvent('keydown', { keyCode: 40 });
                editor.tbody.dispatchEvent( downEvent );

                setTimeout(() =>
                {
                    expect( editor.getSelectedRowIndex() ).toBe(0);
                    done();
                }, 100);
            }, 100);
        });
    });

    describe('Cleanup', () =>
    {
        it('should destroy all event handlers', () =>
        {
            const editor = new TableEditor({
                table: tableElement,
                columns: [
                    { name: 'id', editor: '<input type="text">' }
                ]
            });

            editor.setData([{ id: 1 }]);
            const rowIndexBefore = editor.getSelectedRowIndex();

            editor.destroy();

            editor.selectRowAtIndex( 1 );

            expect( editor.getSelectedRowIndex() ).toBe(rowIndexBefore);
        });

        it('should clear all references', () =>
        {
            const editor = new TableEditor({
                table: tableElement,
                columns: [
                    { name: 'id', editor: '<input type="text">' }
                ]
            });

            editor.destroy();

            expect( editor.tbody ).toBeNull();
            expect( editor.table ).toBeNull();
            expect( editor.toolbar ).toBeNull();
            expect( editor.colDefinitions ).toBeNull();
        });
    });

    describe('Error Handling', () =>
    {
        it('should call onError callback on error', () =>
        {
            let errorReceived = null;

            try
            {
                new TableEditor({
                    table: tableElement,
                    columns: [
                        { name: 'id', editor: '<input type="text">' }
                    ],
                    onError: function(tableEditor, error) {
                        errorReceived = error;
                    }
                });

                new TableEditor({});
            }
            catch( e )
            {
                expect( errorReceived ).not.toBeNull();
                expect( errorReceived.code ).toBe('MISSING_TABLE');
            }
        });
    });

    describe('Phase 3 Features', () =>
    {
        describe('Touch Support', () =>
        {
            it('should handle tap to select row', (done) =>
            {
                const editor = new TableEditor({
                    table: tableElement,
                    columns: [
                        { name: 'id', editor: '<input type="text">' }
                    ]
                });

                editor.setData([{ id: 1 }, { id: 2 }, { id: 3 }]);

                setTimeout(() =>
                {
                    const row = editor.tbody.rows[0];
                    const touchStart = new TouchEvent('touchstart', { touches: [{ clientX: 100, clientY: 100 }] });
                    const touchEnd = new TouchEvent('touchend', { changedTouches: [{ clientX: 100, clientY: 100 }] });

                    editor.tbody.dispatchEvent( touchStart );

                    setTimeout(() =>
                    {
                        editor.tbody.dispatchEvent( touchEnd );

                        setTimeout(() =>
                        {
                            expect( editor.getSelectedRowIndex() ).toBe(0);
                            done();
                        }, 50);
                    }, 50);
                }, 100);
            });

            it('should not select on long press', (done) =>
            {
                const editor = new TableEditor({
                    table: tableElement,
                    enableTouchSupport: true,
                    columns: [
                        { name: 'id', editor: '<input type="text">' }
                    ]
                });

                editor.setData([{ id: 1 }, { id: 2 }]);

                setTimeout(() =>
                {
                    const touchStart = new TouchEvent('touchstart', { touches: [{ clientX: 100, clientY: 100 }] });
                    const touchEnd = new TouchEvent('touchend', { changedTouches: [{ clientX: 200, clientY: 200 }] });

                    editor.tbody.dispatchEvent( touchStart );

                    setTimeout(() =>
                    {
                        editor.tbody.dispatchEvent( touchEnd );

                        setTimeout(() =>
                        {
                            expect( editor.getSelectedRowIndex() ).toBe(-1);
                            done();
                        }, 50);
                    }, 50);
                }, 100);
            });
        });

        describe('Virtual Scrolling', () =>
        {
            it('should enable virtual scrolling when configured', () =>
            {
                const editor = new TableEditor({
                    table: tableElement,
                    enableVirtualScroll: true,
                    rowHeight: 35,
                    columns: [
                        { name: 'id', editor: '<input type="text">' }
                    ]
                });

                expect( editor.enableVirtualScroll ).toBe(true);
                expect( editor.allData.length ).toBe(0);
            });

            it('should render only visible rows', (done) =>
            {
                const editor = new TableEditor({
                    table: tableElement,
                    enableVirtualScroll: true,
                    rowHeight: 35,
                    columns: [
                        { name: 'id', editor: '<input type="text">' }
                    ]
                });

                const data = [];
                for( let i = 0; i < 100; i++ )
                {
                    data.push({ id: i });
                }

                editor.setData( data );

                setTimeout(() =>
                {
                    expect( editor.tbody.rows.length ).toBeLessThan(100);
                    done();
                }, 100);
            });

            it('should update allData when adding rows', () =>
            {
                const editor = new TableEditor({
                    table: tableElement,
                    enableVirtualScroll: true,
                    columns: [
                        { name: 'id', editor: '<input type="text">' }
                    ]
                });

                editor.setData([{ id: 1 }]);
                expect( editor.allData.length ).toBe(1);

                editor.appendRow();
                expect( editor.allData.length ).toBe(2);
            });
        });

        describe('API Enhancements', () =>
        {
            it('should get selected cell', () =>
            {
                const editor = new TableEditor({
                    table: tableElement,
                    columns: [
                        { name: 'id', editor: '<input type="text">' },
                        { name: 'name', editor: '<input type="text">' }
                    ]
                });

                editor.setData([{ id: 1, name: 'Test' }]);
                editor.selectCell( 0, 1 );

                const selectedCell = editor.getSelectedCell();

                expect( selectedCell ).not.toBeNull();
                expect( editor.getValue( 0, 1 ) ).toBe('Test');
            });

            it('should clear deleted rows history', () =>
            {
                const editor = new TableEditor({
                    table: tableElement,
                    columns: [
                        { name: 'id', editor: '<input type="text">' }
                    ]
                });

                editor.setData([{ id: 1 }]);
                editor.deleteRow();

                expect( editor.getDeletedData().length ).toBe(1);

                editor.clearDeletedRows();
                expect( editor.getDeletedData().length ).toBe(0);
            });

            it('should get table statistics', () =>
            {
                const editor = new TableEditor({
                    table: tableElement,
                    columns: [
                        { name: 'id', editor: '<input type="text">' },
                        { name: 'name', editor: '<input type="text">' }
                    ]
                });

                editor.setData([
                    { id: 1, name: 'Row1' },
                    { id: 0, name: '' },
                    { id: 2, name: 'Row2' }
                ]);

                const stats = editor.getStatistics();

                expect( stats.totalRows ).toBe(3);
                expect( stats.emptyRows ).toBe(1);
                expect( stats.editableRows ).toBe(2);
                expect( stats.totalColumns ).toBe(2);
                expect( stats.editableColumns ).toBe(2);
            });
        });

        describe('Performance', () =>
        {
            it('should debounce resize events', (done) =>
            {
                let resizeCount = 0;

                const editor = new TableEditor({
                    table: tableElement,
                    debounceDelay: 50,
                    columns: [
                        { name: 'id', editor: '<input type="text">' }
                    ]
                });

                const resizeEvent = new Event('resize');
                window.dispatchEvent( resizeEvent );
                window.dispatchEvent( resizeEvent );
                window.dispatchEvent( resizeEvent );

                setTimeout(() =>
                {
                    expect( resizeCount ).toBeLessThan(3);
                    done();
                }, 150);
            });

            it('should batch update multiple rows', () =>
            {
                const editor = new TableEditor({
                    table: tableElement,
                    columns: [
                        { name: 'id', editor: '<input type="text">' },
                        { name: 'name', editor: '<input type="text">' }
                    ]
                });

                editor.setData([
                    { id: 1, name: 'Test1' },
                    { id: 2, name: 'Test2' }
                ]);

                const updates = [
                    { index: 0, data: { id: 10, name: 'Updated1' } },
                    { index: 1, data: { id: 20, name: 'Updated2' } }
                ];

                editor._batchRowUpdate_( updates );

                expect( editor.getRowData( 0 ).id ).toBe(10);
                expect( editor.getRowData( 1 ).id ).toBe(20);
            });
        });
    });
});
